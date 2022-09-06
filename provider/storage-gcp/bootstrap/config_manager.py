from asyncio.log import logger
import logging
import os

from osdu_api.configuration.base_config_manager import BaseConfigManager

logger = logging.getLogger()


class ConfigManager(BaseConfigManager):
    """Custom config manager for Python SDK
    """

    def __init__(self) -> None:
        try:
            self.config = {
                "environment": {
                    "storage_url": os.environ["STORAGE_URL"],
                    "data_partition_id": os.environ["DATA_PARTITION_ID"],
                    "use_service_principal": False
                },
                "provider": {
                    "name": os.environ["CLOUD_PROVIDER"]
                }
            }
        except KeyError as error:
            logger.error(
                "One or more of the following environmental variables weren't set:\n"
                "STORAGE_URL,\n"
                "DATA_PARTITION_ID,\n"
                "CLOUD_PROVIDER."
            )
            raise error

    def get(self, section: str, option: str) -> str:
        try:
            config_value = self.config[section][option]
        except KeyError:
            config_value = "stub"
        return config_value

    def getint(self, section: str, option: str) -> int:
        try:
            config_value = self.config[section][option]
        except KeyError:
            config_value = 0
        return config_value

    def getfloat(self, section: str, option: str) -> float:
        try:
            config_value = self.config[section][option]
        except KeyError:
            config_value = 0.0
        return config_value

    def getbool(self, section: str, option: str, default=False) -> bool:
        try:
            config_value = self.config[section][option]
        except KeyError:
            config_value = default
        return config_value
