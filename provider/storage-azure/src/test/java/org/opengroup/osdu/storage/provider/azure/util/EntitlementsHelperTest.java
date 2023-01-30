package org.opengroup.osdu.storage.provider.azure.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.http.DpsHeaders;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.storage.provider.azure.EntitlementsAndCacheServiceAzure;

import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EntitlementsHelperTest {

    private static final String OWNER_ACL = "owner_acl";
    private static final String VIEWER_ACL = "viewer_acl";

    private static final String USER = "test_user";

    @Mock
    private DpsHeaders headers;

    @Mock
    private EntitlementsAndCacheServiceAzure dataEntitlementsService;

    @InjectMocks
    private EntitlementsHelper entitlementsHelper;

    @Mock
    private RecordMetadata recordMetadata;

    @Mock
    private Acl acl;

    private final ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);


    @Before
    public void setup() {
        when(recordMetadata.getAcl()).thenReturn(acl);
        when(recordMetadata.getUser()).thenReturn(USER);
        when(acl.getOwners()).thenReturn(new String[]{OWNER_ACL});
        when(acl.getViewers()).thenReturn(new String[]{VIEWER_ACL});
    }

    @Test
    public void hasOwnerAccessToRecord_shouldReturnTrue_ifUserIsOwner() {
        when(dataEntitlementsService.hasAccessToData(eq(headers), anySet())).thenReturn(true);

        assertTrue(entitlementsHelper.hasOwnerAccessToRecord(recordMetadata));

        verify(dataEntitlementsService, only()).hasAccessToData(eq(headers), captor.capture());
        assertTrue(captor.getValue().contains(OWNER_ACL));
        assertEquals(1, captor.getValue().size());
    }

    @Test
    public void hasOwnerAccessToRecord_shouldReturnFalse_ifUserIsNotOwner() {
        when(dataEntitlementsService.hasAccessToData(eq(headers), anySet())).thenReturn(false);

        assertFalse(entitlementsHelper.hasOwnerAccessToRecord(recordMetadata));

        verify(dataEntitlementsService, only()).hasAccessToData(eq(headers), captor.capture());
        assertTrue(captor.getValue().contains(OWNER_ACL));
        assertEquals(1, captor.getValue().size());
    }

    @Test
    public void hasOwnerAccessToRecord_shouldReturnFalse_ifRecordIsNull() {
        when(dataEntitlementsService.hasAccessToData(eq(headers), anySet())).thenReturn(false);

        assertFalse(entitlementsHelper.hasOwnerAccessToRecord(null));

        verify(dataEntitlementsService, only()).hasAccessToData(eq(headers), captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    @Test
    public void hasOwnerAccessToRecord_shouldReturnFalse_ifRecordsAclsAreNull() {
        when(recordMetadata.getAcl()).thenReturn(null);
        when(dataEntitlementsService.hasAccessToData(eq(headers), anySet())).thenReturn(false);

        assertFalse(entitlementsHelper.hasOwnerAccessToRecord(recordMetadata));

        verify(dataEntitlementsService, only()).hasAccessToData(eq(headers), captor.capture());
        assertTrue(captor.getValue().isEmpty());
    }

    //-----
    @Test
    public void hasViewerAccessToRecord_shouldReturnTrue_ifUserIsViewer() {
        when(dataEntitlementsService.hasAccessToData(eq(headers), anySet())).thenReturn(true);

        assertTrue(entitlementsHelper.hasViewerAccessToRecord(recordMetadata));

        verify(dataEntitlementsService, only()).hasAccessToData(eq(headers), captor.capture());
        assertTrue(captor.getValue().contains(VIEWER_ACL));
        assertEquals(1, captor.getValue().size());
    }

    @Test
    public void hasViewerAccessToRecord_shouldReturnTrue_ifUserIsNotViewerButOwner() {
        when(dataEntitlementsService.hasAccessToData(eq(headers),
                argThat((ArgumentMatcher<Set>) set -> set.contains(VIEWER_ACL)))).thenReturn(false);
        when(dataEntitlementsService.hasAccessToData(eq(headers),
                argThat((ArgumentMatcher<Set>) set -> set.contains(OWNER_ACL)))).thenReturn(true);

        assertTrue(entitlementsHelper.hasViewerAccessToRecord(recordMetadata));

        verify(dataEntitlementsService, times(2)).hasAccessToData(eq(headers), captor.capture());
        assertEquals(2, captor.getAllValues().size());
        assertTrue(captor.getAllValues().get(0).contains(VIEWER_ACL));
        assertEquals(1, captor.getAllValues().get(0).size());
        assertTrue(captor.getAllValues().get(1).contains(OWNER_ACL));
        assertEquals(1, captor.getAllValues().get(1).size());
    }


    @Test
    public void hasViewerAccessToRecord_shouldReturnTrue_ifUserIsNotViewerButCreator() {
        when(dataEntitlementsService.hasAccessToData(eq(headers),
                argThat((ArgumentMatcher<Set>) set -> set.contains(VIEWER_ACL)))).thenReturn(false);
        when(headers.getUserEmail()).thenReturn(USER);

        assertTrue(entitlementsHelper.hasViewerAccessToRecord(recordMetadata));

        verify(dataEntitlementsService, only()).hasAccessToData(eq(headers), captor.capture());
        assertTrue(captor.getValue().contains(VIEWER_ACL));
    }

    @Test
    public void hasViewerAccessToRecord_shouldReturnFalse_ifRecordIsNull() {
        when(dataEntitlementsService.hasAccessToData(eq(headers), anySet())).thenReturn(false);

        assertFalse(entitlementsHelper.hasViewerAccessToRecord(null));

        verify(dataEntitlementsService, times(2)).hasAccessToData(eq(headers), captor.capture());
        assertEquals(2, captor.getAllValues().size());
        assertTrue(captor.getAllValues().get(0).isEmpty());
        assertTrue(captor.getAllValues().get(1).isEmpty());
    }

    @Test
    public void hasViewerAccessToRecord_shouldReturnFalse_ifRecordsAclsAreNull() {
        when(recordMetadata.getAcl()).thenReturn(null);
        when(dataEntitlementsService.hasAccessToData(eq(headers), anySet())).thenReturn(false);

        assertFalse(entitlementsHelper.hasViewerAccessToRecord(recordMetadata));

        verify(dataEntitlementsService, times(2)).hasAccessToData(eq(headers), captor.capture());
        assertTrue(captor.getAllValues().get(0).isEmpty());
        assertTrue(captor.getAllValues().get(1).isEmpty());
    }
}
