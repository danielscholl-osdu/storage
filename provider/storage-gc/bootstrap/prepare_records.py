import json
import re
from typing import Iterator

from osdu_api.model.storage.acl import Acl
from osdu_api.model.storage.legal import Legal
from osdu_api.model.storage.record import Record as Record
from osdu_api.model.storage.record_ancestry import RecordAncestry


class RecordsPreparer:
    """
    This class is used for preparing records from the Manifest to be sent via StorageClient
    """

    RECORD_ID_REGEX = r"\"([\w\-\.\{\}]+)(:[\w\-\.]+\-\-[\w\-\.]+:[\w\-\.\:\%]+)\""
    RECORD_KIND_REGEX = r"\"([\w\-\.]+)(:[\w\-\.]+:[\w\-\.]+:[0-9]+.[0-9]+.[0-9]+)\""

    def __init__(
        self,
        data_partition_id: str,
        acl_owners: list,
        acl_viewers: list,
        legaltags: list,
        country_codes: str = ["US"]
    ) -> None:
        self.data_partition_id = data_partition_id
        self.acl = Acl(viewers=acl_viewers, owners=acl_owners)
        self.legal = Legal(
            legaltags=legaltags,
            other_relevant_data_countries=country_codes,
            status=""
        )
        # FIXME: Status is not working with Storage service. Need to investigate this problem further
        try:
            del self.legal.status
        except AttributeError:
            pass

    def _data_partition_id_repl_func(self, match: re.Match) -> str:
        value_without_data_partition = match.group(2)
        return f"\"{self.data_partition_id}{value_without_data_partition}\""

    def _replace_data_partition_id(self, manifest_content: str) -> str:
        """Replace data-partition-id in the Manifest

        :param manifest_content: Manifest content
        :type manifest_content: str
        :return: Manifest content with replaced data-partition-id
        :rtype: str
        """
        manifest_content = re.sub(
            self.RECORD_ID_REGEX,
            self._data_partition_id_repl_func,
            manifest_content
        )
        return manifest_content

    def _prepare_record(self, record_dict: dict) -> Record:
        record_ancestry = RecordAncestry(
            parents=record_dict.get("ancestry", {}).get("parents", [])
        )
        record = Record(
            kind=record_dict["kind"],
            acl=self.acl,
            legal=self.legal,
            id=record_dict.get("id"),
            data=record_dict["data"],
            meta=record_dict.get("meta"),
            ancestry=record_ancestry
        )
        return record

    def manifest_records(self, manifest_string: str) -> Iterator[Record]:
        """Generator that gets a Manifest file as a string,
        applies changes to it and yields osdu_api model Record ony by one.

        :param manifest_string: content of the Manifest file
        :type manifest_string: str
        :yield: osdu_api model Record object
        :rtype: Iterator[Record]
        """
        manifest_string = self._replace_data_partition_id(manifest_string)
        manifest_dict = json.loads(manifest_string)
        record_list = manifest_dict.get(
            "ReferenceData", []) + manifest_dict.get("MasterData", [])
        for record_dict in record_list:
            yield self._prepare_record(record_dict)
