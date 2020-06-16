package org.opengroup.osdu.storage.provider.ibm.jobs;

import static java.util.Collections.singletonList;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.indexer.OperationType;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.legal.jobs.ComplianceChangeInfo;
import org.opengroup.osdu.core.common.model.legal.jobs.ILegalComplianceChangeService;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChanged;
import org.opengroup.osdu.core.common.model.legal.jobs.LegalTagChangedCollection;
import org.opengroup.osdu.core.common.model.storage.PubSubInfo;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.storage.logging.StorageAuditLogger;
import org.opengroup.osdu.storage.provider.ibm.cache.LegalTagCache;
import org.opengroup.osdu.storage.provider.interfaces.IMessageBus;
import org.opengroup.osdu.storage.provider.interfaces.IRecordsMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LegalComplianceChangeServiceImpl implements ILegalComplianceChangeService {

	private final static String incompliantName = "incompliant";
	private final static String compliantName = "compliant";

	@Inject
    private IRecordsMetadataRepository<String> recordsMetadataRepository;

	@Inject
	private IMessageBus storageMessageBus;

	@Inject
	private StorageAuditLogger auditLogger;

	private static final Logger logger = LoggerFactory.getLogger(LegalComplianceChangeServiceImpl.class);

	@Inject
	private LegalTagCache legalTagCache;

	@Override
    public Map<String, LegalCompliance> updateComplianceOnRecords(LegalTagChangedCollection legalTagsChanged,
                                                                  DpsHeaders headers) {

    	// TODO implement the validation of the PUBSUB_TOKEN from the query param 'token'

		Map<String, LegalCompliance> output = new HashMap<>();

		// TODO: optimize to not have while loop inside a for each
		// We should only get one legal tag change from the queue, the model should
		// reflect that
		for (LegalTagChanged lt : legalTagsChanged.getStatusChangedTags()) {

			ComplianceChangeInfo complianceChangeInfo = this.getComplianceChangeInfo(lt);
			if (complianceChangeInfo == null) {
				continue;
			}

			AbstractMap.SimpleEntry<String, List<RecordMetadata>> results;
			String cursor = null;
			do {
                results = this.recordsMetadataRepository
                        .queryByLegalTagName(lt.getChangedTagName(), 500, cursor);
				cursor = results.getKey();
				List<RecordMetadata> recordsMetadata = results.getValue();
				PubSubInfo[] pubsubInfos = this.updateComplianceStatus(complianceChangeInfo, recordsMetadata, output);

				if (lt.getChangedTagStatus() == incompliantName) {
					for (RecordMetadata rmd : recordsMetadata) {
						this.recordsMetadataRepository.delete(rmd.getId());
					}
				} else {
					this.recordsMetadataRepository.createOrUpdate(recordsMetadata);
				}

				StringBuilder recordsId = new StringBuilder();
				for (RecordMetadata recordMetadata : recordsMetadata) {
					recordsId.append(", ").append(recordMetadata.getId());
				}
				this.auditLogger.updateRecordsComplianceStateSuccess(
						singletonList("[" + recordsId.toString().substring(2) + "]"));

				this.storageMessageBus.publishMessage(headers, pubsubInfos);
			} while (cursor != null);
		}

		return output;
	}

	private PubSubInfo[] updateComplianceStatus(ComplianceChangeInfo complianceChangeInfo,
			List<RecordMetadata> recordMetadata, Map<String, LegalCompliance> output) {
		PubSubInfo[] pubsubInfo = new PubSubInfo[recordMetadata.size()];

		int i = 0;
		for (RecordMetadata rm : recordMetadata) {
			rm.getLegal().setStatus(complianceChangeInfo.getNewState());
			rm.setStatus(complianceChangeInfo.getNewRecordState());
			pubsubInfo[i] = new PubSubInfo(rm.getId(), rm.getKind(), complianceChangeInfo.getPubSubEvent());
			output.put(rm.getId(), complianceChangeInfo.getNewState());
			i++;
		}

		return pubsubInfo;
	}

	private ComplianceChangeInfo getComplianceChangeInfo(LegalTagChanged lt) {
		ComplianceChangeInfo output = null;

		if (lt.getChangedTagStatus().equalsIgnoreCase(compliantName)) {
			output = new ComplianceChangeInfo(LegalCompliance.compliant, OperationType.create, RecordState.active);
		} else if (lt.getChangedTagStatus().equalsIgnoreCase(incompliantName)) {
			this.legalTagCache.delete(lt.getChangedTagName());
			output = new ComplianceChangeInfo(LegalCompliance.incompliant, OperationType.delete, RecordState.deleted);
		} else {
			logger.warn(String.format("Unknown LegalTag compliance status received %s %s",
					lt.getChangedTagStatus(), lt.getChangedTagName()));
		}

		return output;
	}

}
