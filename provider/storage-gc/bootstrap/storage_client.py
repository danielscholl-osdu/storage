from typing import List

from osdu_api.clients.base_client import BaseClient
from osdu_api.model.http_method import HttpMethod
from osdu_api.model.storage.query_records_request import QueryRecordsRequest
from osdu_api.model.storage.record import Record
from osdu_api.clients.storage.record_client import RecordClient


class StorageClient(RecordClient):

    def create_update_records(self, records: List[Record], bearer_token=None):
        """
        Need to override this method to add extra query argument to skip duplicates.
        """
        records_data = '['
        for record in records:
            records_data = '{}{}{}'.format(records_data, record.to_JSON(), ',')
        records_data = records_data[:-1]
        records_data = '{}{}'.format(records_data, ']')
        return self.make_request(method=HttpMethod.PUT, url='{}{}'.format(self.storage_url, '/records?skipdupes=true'), data=records_data, bearer_token=bearer_token)
