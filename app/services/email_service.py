"""Envio de email transacional (feature 013 — recuperação de senha).

Nenhum provedor SMTP gratuito foi contratado ainda (Princípio II — custo
zero); enquanto `SMTP_HOST`/`SMTP_USER`/`SMTP_PASSWORD` não estiverem
configurados em `.env` (`Agro_Tec_infra`), o envio cai para o log em vez de
fingir que um email foi entregue — nunca simular sucesso de uma integração
que não está de fato configurada.
"""

import logging
from abc import ABC, abstractmethod

from app.core.config import Settings

logger = logging.getLogger(__name__)


class EmailService(ABC):
    @abstractmethod
    async def enviar_recuperacao_senha(self, destinatario: str, link: str) -> None: ...


class SmtpEmailService(EmailService):
    """Envio real via SMTP (`aiosmtplib`), usado quando as credenciais estão
    configuradas — troca de provedor não afeta o restante do código (research.md)."""

    def __init__(self, host: str, port: int, usuario: str, senha: str, remetente: str) -> None:
        self._host = host
        self._port = port
        self._usuario = usuario
        self._senha = senha
        self._remetente = remetente

    async def enviar_recuperacao_senha(self, destinatario: str, link: str) -> None:
        from email.message import EmailMessage

        import aiosmtplib

        mensagem = EmailMessage()
        mensagem["From"] = self._remetente
        mensagem["To"] = destinatario
        mensagem["Subject"] = "Redefinição de senha — AgroClima Pará"
        mensagem.set_content(
            f"Para redefinir sua senha, acesse o link abaixo (válido por 1 hora):\n\n{link}\n\n"
            "Se você não solicitou isso, ignore este email."
        )
        await aiosmtplib.send(
            mensagem,
            hostname=self._host,
            port=self._port,
            username=self._usuario,
            password=self._senha,
            start_tls=True,
        )


class LogEmailService(EmailService):
    """Fallback quando não há provedor SMTP configurado — registra o link no
    log da aplicação em vez de uma entrega real (ver docstring do módulo)."""

    async def enviar_recuperacao_senha(self, destinatario: str, link: str) -> None:
        # .warning(), não .info(): a aplicação não configura nível de log em
        # lugar nenhum (nível padrão do Python é WARNING) — um .info() aqui
        # seria descartado silenciosamente e o link, perdido de fato.
        logger.warning(
            "SMTP não configurado — email de recuperação de senha para %s: %s", destinatario, link
        )


def obter_email_service(settings: Settings) -> EmailService:
    if settings.smtp_host and settings.smtp_user and settings.smtp_password:
        return SmtpEmailService(
            host=settings.smtp_host,
            port=settings.smtp_port,
            usuario=settings.smtp_user,
            senha=settings.smtp_password,
            remetente=settings.smtp_from or settings.smtp_user,
        )
    return LogEmailService()
