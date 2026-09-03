import json
import logging
import uuid
from typing import Annotated, Any

from fastapi import APIRouter, Depends, File, Form, UploadFile
from geoalchemy2.functions import ST_Area, ST_AsGeoJSON, ST_Intersection, ST_Overlaps
from geoalchemy2.shape import from_shape
from geoalchemy2.types import Geography
from pydantic import BaseModel
from shapely.geometry import shape
from sqlalchemy import cast, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.calculos.psicrometria import calcular_delta_t
from app.core.calculos.pulverizacao import (
    ClassificacaoPulverizacao,
    classificar_delta_t,
    classificar_pulverizacao,
)
from app.core.calculos.solo import calcular_cad, classificar_textura, estimar_cc_pmp
from app.core.geo.validacao_geometria import esta_dentro_do_para, geometria_valida
from app.core.response import AppError, envelope_sucesso
from app.core.security import UsuarioAutenticado, get_current_user
from app.db.models.balanco_hidrico_diario import BalancoHidricoDiario
from app.db.models.talhao import Talhao, TipoSolo
from app.db.queries.estacao_proxima import buscar_estacoes_mais_proximas
from app.db.session import get_db
from app.services.clima_tempo_real_service import obter_clima_atual
from app.services.importacao_geo_service import (
    ArquivoGeoInvalidoError,
    extrair_geometria,
    normalizar_para_multipolygon,
)
from app.services.soilgrids_service import FonteSoloIndisponivelError, parametrizar_solo

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/talhoes", tags=["talhoes"])

AREA_MINIMA_SOBREPOSICAO_M2 = 10.0
DEFAULT_PAGE_SIZE = 20


class TalhaoCreate(BaseModel):
    propriedade_id: uuid.UUID
    nome: str
    geometria: dict[str, Any]
    confirmar_fora_do_para: bool = False


class TalhaoRead(BaseModel):
    id: uuid.UUID
    propriedade_id: uuid.UUID
    nome: str
    geometria: dict[str, Any]
    area_ha: float
    tipo_solo: TipoSolo | None = None
    capacidade_agua_disponivel_mm: float | None = None
    aviso: str | None = None


async def _serializar(db: AsyncSession, talhao: Talhao, aviso: str | None = None) -> TalhaoRead:
    resultado = await db.execute(select(ST_AsGeoJSON(talhao.geometria)))
    return TalhaoRead(
        id=talhao.id,
        propriedade_id=talhao.propriedade_id,
        nome=talhao.nome,
        geometria=json.loads(resultado.scalar_one()),
        area_ha=float(talhao.area_ha),
        tipo_solo=talhao.tipo_solo,
        capacidade_agua_disponivel_mm=(
            float(talhao.capacidade_agua_disponivel_mm)
            if talhao.capacidade_agua_disponivel_mm is not None
            else None
        ),
        aviso=aviso,
    )


async def _parametrizar_solo_do_talhao(talhao: Talhao, poligono) -> None:
    """FR-001-FR-004 (feature 004) — classifica textura e calcula CAD a partir do
    centroide do talhão; nunca bloqueia o cadastro se a fonte falhar (FR-006/T009)."""
    centroide = poligono.centroid
    try:
        perfil = await parametrizar_solo(centroide.y, centroide.x)
    except FonteSoloIndisponivelError:
        logger.warning("SoilGrids indisponível para talhão %s — solo fica nulo", talhao.id)
        return

    if perfil is None:
        return  # sem cobertura para a coordenada — talhão salvo com campos de solo nulos

    talhao.tipo_solo = classificar_textura(
        perfil.fracao_argila_pct, perfil.fracao_areia_pct, perfil.fracao_silte_pct
    )
    talhao.fracao_argila_pct = perfil.fracao_argila_pct
    talhao.fracao_areia_pct = perfil.fracao_areia_pct
    talhao.fracao_silte_pct = perfil.fracao_silte_pct
    talhao.materia_organica_pct = perfil.materia_organica_pct

    capacidade_campo_pct, ponto_murcha_pct = estimar_cc_pmp(
        perfil.fracao_argila_pct, perfil.fracao_areia_pct, perfil.materia_organica_pct
    )
    talhao.capacidade_agua_disponivel_mm = calcular_cad(
        capacidade_campo_pct=capacidade_campo_pct,
        ponto_murcha_permanente_pct=ponto_murcha_pct,
        densidade_solo_g_cm3=perfil.densidade_solo_g_cm3,
    )


async def _calcular_area_ha(db: AsyncSession, geometria_wkb) -> float:
    """Área real (m²→ha) via projeção geográfica (`::geography`), não planar em
    graus — RF012/Princípio IV: nunca cálculo aproximado em aplicação."""
    resultado = await db.execute(select(ST_Area(cast(geometria_wkb, Geography)) / 10000))
    return resultado.scalar_one()


async def _buscar_sobreposicao_mesma_propriedade(
    db: AsyncSession, propriedade_id: uuid.UUID, geometria_wkb
) -> str | None:
    """RN015 — nome do talhão sobreposto (>10m² de interseção) na mesma
    propriedade, ou None. Tudo via operadores nativos do PostGIS."""
    resultado = await db.execute(
        select(Talhao.nome).where(
            Talhao.propriedade_id == propriedade_id,
            ST_Overlaps(Talhao.geometria, geometria_wkb),
            ST_Area(cast(ST_Intersection(Talhao.geometria, geometria_wkb), Geography))
            > AREA_MINIMA_SOBREPOSICAO_M2,
        )
    )
    return resultado.scalars().first()


async def _buscar_sobreposicao_outra_propriedade(
    db: AsyncSession, propriedade_id: uuid.UUID, geometria_wkb
) -> str | None:
    """RN015 — sobreposição com talhão de OUTRA propriedade é permitida, mas
    sinalizada (pode ser divisa em disputa, fora do escopo do sistema resolver)."""
    resultado = await db.execute(
        select(Talhao.nome).where(
            Talhao.propriedade_id != propriedade_id,
            ST_Overlaps(Talhao.geometria, geometria_wkb),
            ST_Area(cast(ST_Intersection(Talhao.geometria, geometria_wkb), Geography))
            > AREA_MINIMA_SOBREPOSICAO_M2,
        )
    )
    return resultado.scalars().first()


async def _criar_talhao_a_partir_de_poligono(
    db: AsyncSession,
    propriedade_id: uuid.UUID,
    nome: str,
    poligono,
    confirmar_fora_do_para: bool,
) -> tuple[Talhao, str | None]:
    """Núcleo do cadastro de talhão (RN015, RN016) — reaproveitado pelo
    cadastro manual (JSON) e pela importação de arquivo (T012)."""
    if not geometria_valida(poligono):
        raise AppError(422, "Geometria inválida.")

    if not esta_dentro_do_para(poligono.centroid) and not confirmar_fora_do_para:
        raise AppError(
            422,
            "Talhão fora da área esperada do Pará. Confirme para prosseguir.",
            {"tipo": "FORA_DO_PARA", "requer_confirmacao": True},
        )

    # Escopo V3 — coluna é MultiPolygon; um Polygon isolado (desenho manual,
    # GeoJSON simples) é normalizado para MultiPolygon de uma parte só.
    geometria_wkb = from_shape(normalizar_para_multipolygon(poligono), srid=4326)
    talhao_sobreposto = await _buscar_sobreposicao_mesma_propriedade(
        db, propriedade_id, geometria_wkb
    )
    if talhao_sobreposto:
        raise AppError(
            409,
            f"Geometria sobrepõe o talhão '{talhao_sobreposto}' na mesma propriedade.",
            {"tipo": "SOBREPOSICAO"},
        )

    talhao_outra_propriedade = await _buscar_sobreposicao_outra_propriedade(
        db, propriedade_id, geometria_wkb
    )
    aviso = (
        f"Geometria sobrepõe o talhão '{talhao_outra_propriedade}' de outra propriedade "
        "— pode ser uma divisa em disputa; o sistema não bloqueia, mas verifique."
        if talhao_outra_propriedade
        else None
    )

    area_ha = await _calcular_area_ha(db, geometria_wkb)
    talhao = Talhao(propriedade_id=propriedade_id, nome=nome, geometria=geometria_wkb, area_ha=area_ha)
    db.add(talhao)
    await db.flush()

    await _parametrizar_solo_do_talhao(talhao, poligono)  # feature 004 — nunca bloqueia (FR-006)

    await db.commit()
    await db.refresh(talhao)
    return talhao, aviso


@router.post("")
async def criar_talhao(
    payload: TalhaoCreate,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """FR-002/FR-004, RN015, RN016 — cadastro de talhão com validações geométricas."""
    poligono = shape(payload.geometria)
    talhao, aviso = await _criar_talhao_a_partir_de_poligono(
        db, payload.propriedade_id, payload.nome, poligono, payload.confirmar_fora_do_para
    )
    return envelope_sucesso((await _serializar(db, talhao, aviso)).model_dump(mode="json"))


@router.post("/importar")
async def importar_talhao(
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
    propriedade_id: Annotated[uuid.UUID, Form()],
    nome: Annotated[str, Form()],
    arquivo: Annotated[UploadFile, File()],
    confirmar_fora_do_para: Annotated[bool, Form()] = False,
) -> dict:
    """FR-005 — importa a geometria (Polygon ou MultiPolygon) de um GeoJSON, KML ou Shapefile."""
    conteudo = await arquivo.read()
    try:
        poligono = extrair_geometria(arquivo.filename or "", conteudo)
    except ArquivoGeoInvalidoError as exc:
        raise AppError(422, str(exc)) from exc

    talhao, aviso = await _criar_talhao_a_partir_de_poligono(
        db, propriedade_id, nome, poligono, confirmar_fora_do_para
    )
    return envelope_sucesso((await _serializar(db, talhao, aviso)).model_dump(mode="json"))


@router.get("")
async def listar_talhoes(
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
    propriedade_id: uuid.UUID | None = None,
    page: int = 1,
    page_size: int = DEFAULT_PAGE_SIZE,
) -> dict:
    """FR-002, RNF017 — listagem paginada, com filtro opcional por propriedade."""
    query = select(Talhao)
    if propriedade_id is not None:
        query = query.where(Talhao.propriedade_id == propriedade_id)

    total = (await db.execute(select(func.count()).select_from(query.subquery()))).scalar_one()
    resultado = await db.execute(query.offset((page - 1) * page_size).limit(page_size))
    talhoes = resultado.scalars().all()
    itens = [(await _serializar(db, t)).model_dump(mode="json") for t in talhoes]
    return envelope_sucesso({"itens": itens, "total": total, "page": page, "page_size": page_size})


async def _buscar_talhao_ou_404(db: AsyncSession, talhao_id: uuid.UUID) -> Talhao:
    talhao = await db.get(Talhao, talhao_id)
    if talhao is None:
        raise AppError(404, "Talhão não encontrado.")
    return talhao


@router.get("/{talhao_id}")
async def obter_talhao(
    talhao_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    talhao = await _buscar_talhao_ou_404(db, talhao_id)
    return envelope_sucesso((await _serializar(db, talhao)).model_dump(mode="json"))


@router.get("/{talhao_id}/estacao-mais-proxima")
async def obter_estacao_mais_proxima(
    talhao_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """RF015/RF016/RF035 (feature 006, Escopo V3) — as 3 estações INMET mais
    próximas do talhão, usadas na interpolação IDW (calculos-geo-metero.md §1B)."""
    talhao = await _buscar_talhao_ou_404(db, talhao_id)
    resultados = await buscar_estacoes_mais_proximas(db, talhao)
    if not resultados:
        raise AppError(404, "Nenhuma estação disponível.")
    return envelope_sucesso(
        {
            "estacoes": [
                {
                    "estacao_codigo": r.estacao_codigo,
                    "municipio": r.municipio,
                    "distancia_km": r.distancia_km,
                    "latitude": r.latitude,
                    "longitude": r.longitude,
                }
                for r in resultados
            ]
        }
    )


@router.get("/{talhao_id}/pulverizacao")
async def obter_pulverizacao(
    talhao_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """RF022/RF023/RF036 (feature 009, Escopo V3) — classificação da janela de
    pulverização combinando vento (RN001-RN003) e Delta T (RN021/RN022),
    ambas a partir da leitura mais recente (feature 008/006), checagens
    independentes e complementares — ver Adendo V3 em REQUISITOS.md."""
    talhao = await _buscar_talhao_ou_404(db, talhao_id)
    clima = await obter_clima_atual(db, talhao)
    if clima is None or clima.vento_kmh is None:
        # T005 — nunca apresenta uma classificação como se fosse válida sem dado.
        raise AppError(404, "Sem leitura de vento disponível para classificar a pulverização.")

    classificacao_vento = classificar_pulverizacao(clima.vento_kmh, clima.rajada_kmh or 0.0)

    classificacao_delta_t = None
    delta_t_c = None
    if clima.temperatura_c is not None and clima.umidade_pct is not None:
        delta_t_c = calcular_delta_t(clima.temperatura_c, clima.umidade_pct)
        classificacao_delta_t = classificar_delta_t(delta_t_c)

    motivos_bloqueio = [
        c
        for c in (classificacao_vento, classificacao_delta_t)
        if c is not None and c != ClassificacaoPulverizacao.FAVORAVEL
    ]
    classificacao_final = motivos_bloqueio[0] if motivos_bloqueio else ClassificacaoPulverizacao.FAVORAVEL

    return envelope_sucesso(
        {
            "classificacao": classificacao_final.value,
            "motivos_bloqueio": [c.value for c in motivos_bloqueio],
            "vento_kmh": clima.vento_kmh,
            "rajada_kmh": clima.rajada_kmh,
            "delta_t_c": round(delta_t_c, 1) if delta_t_c is not None else None,
            "fonte_dados": clima.fonte_dados.value,
        }
    )


@router.get("/{talhao_id}/balanco-hidrico")
async def obter_balanco_hidrico(
    talhao_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> dict:
    """Feature 010 — leitura do balanço hídrico mais recente (uso interno/debug;
    contrato em `contracts/balanco-hidrico.md`)."""
    talhao = await _buscar_talhao_ou_404(db, talhao_id)
    resultado = await db.execute(
        select(BalancoHidricoDiario)
        .where(BalancoHidricoDiario.talhao_id == talhao.id)
        .order_by(BalancoHidricoDiario.data.desc())
        .limit(1)
    )
    registro = resultado.scalars().first()
    if registro is None:
        raise AppError(404, "Balanço hídrico ainda não calculado para este talhão.")

    if talhao.capacidade_agua_disponivel_mm:
        percentual_cad = (
            float(registro.armazenamento_mm) / float(talhao.capacidade_agua_disponivel_mm) * 100
        )
    else:
        percentual_cad = None

    return envelope_sucesso(
        {
            "data": registro.data.isoformat(),
            "armazenamento_mm": float(registro.armazenamento_mm),
            "cad_mm": (
                float(talhao.capacidade_agua_disponivel_mm)
                if talhao.capacidade_agua_disponivel_mm
                else None
            ),
            "percentual_cad": percentual_cad,
        }
    )


@router.delete("/{talhao_id}", status_code=204)
async def excluir_talhao(
    talhao_id: uuid.UUID,
    usuario: Annotated[UsuarioAutenticado, Depends(get_current_user)],
    db: Annotated[AsyncSession, Depends(get_db)],
) -> None:
    """FR-002 — exclusão isolada do talhão (não afeta a propriedade nem outros talhões)."""
    talhao = await _buscar_talhao_ou_404(db, talhao_id)
    await db.delete(talhao)
    await db.commit()
