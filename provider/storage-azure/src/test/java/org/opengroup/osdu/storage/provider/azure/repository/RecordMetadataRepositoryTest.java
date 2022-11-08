package org.opengroup.osdu.storage.provider.azure.repository;

import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.ExpectedException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opengroup.osdu.core.common.logging.JaxRsDpsLog;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;

import java.util.Optional;

import static java.util.Collections.singletonList;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class RecordMetadataRepositoryTest {

    @Mock
    private JaxRsDpsLog logger;

    @InjectMocks
    private RecordMetadataRepository recordMetadataRepository;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();


    @Test
    public void shouldFailOnCreateOrUpdate_IfAclIsNull() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage("Acl of the record must not be null");
        try {
            recordMetadataRepository.createOrUpdate(singletonList(new RecordMetadata()), Optional.empty());
        } catch (IllegalArgumentException e) {
            verify(logger, only()).error("Acl of the record RecordMetadata(id=null, kind=null, previousVersionKind=null, acl=null, legal=null, ancestry=null, tags={}, gcsVersionPaths=[], status=null, user=null, createTime=0, modifyUser=null, modifyTime=0) must not be null");
        }

    }


}
