package org.opengroup.osdu.storage.provider.mongodb.util;

import org.opengroup.osdu.core.common.model.entitlements.Acl;
import org.opengroup.osdu.core.common.model.legal.Legal;
import org.opengroup.osdu.core.common.model.legal.LegalCompliance;
import org.opengroup.osdu.core.common.model.storage.RecordMetadata;
import org.opengroup.osdu.core.common.model.storage.RecordState;
import org.opengroup.osdu.core.common.model.storage.Schema;
import org.opengroup.osdu.core.common.model.storage.SchemaItem;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Generator {

    private static final String[] NAMESET = {"tenant", "source", "foo", "bar", "foobar", "exo", "zoo", "rsq"};
    private static final Random RAND = new Random();

    public static String kind() {
        return String.format("opendes:%s:%s:%d.%d.%d",
                NAMESET[RAND.nextInt(NAMESET.length)], NAMESET[RAND.nextInt(NAMESET.length)],
                RAND.nextInt(10), RAND.nextInt(10), RAND.nextInt(10));
    }

    public static String path() {
        return path(4);
    }

    public static String path(int segments) {
        return IntStream.range(0, segments)
                .mapToObj(i -> (Math.random() > 0.5) ? NAMESET[RAND.nextInt(NAMESET.length)] : String.valueOf(RAND.nextInt(10)))
                .reduce((a, b) -> String.format("%s/%s", a, b))
                .orElse("defPath");
    }

    public static Schema schema(String kind) {
        SchemaItem[] schemaItems = new SchemaItem[5];
        for (int i = 0; i < schemaItems.length; i++) {
            SchemaItem item = new SchemaItem();
            item.setKind(Generator.kind());
            item.setPath(Generator.path());
            item.setExt(new HashMap<>());
            item.getExt().put("value", 142 * i);
            schemaItems[i] = item;
        }
        return Schema.builder()
                .schema(schemaItems)
                .kind(kind)
                .ext(new HashMap<>())
                .build();
    }

    public static List<RecordMetadata> records(String kind, String user, int size) {
        return IntStream.range(0, size).mapToObj(i -> {
            int id = Math.abs(RAND.nextInt());
            RecordMetadata recordMetadata = new RecordMetadata();
            recordMetadata.setId("opendes:id:" + id);
            recordMetadata.setKind(kind);

            Acl recordAcl = new Acl();
            String[] owners = {"data.tenant@byoc.local"};
            String[] viewers = {"data.tenant@byoc.local"};
            recordAcl.setOwners(owners);
            recordAcl.setViewers(viewers);
            recordMetadata.setAcl(recordAcl);

            Legal recordLegal = new Legal();
            Set<String> legalTags = new HashSet<>(Collections.singletonList("opendes-storage-" + id));
            recordLegal.setLegaltags(legalTags);
            LegalCompliance status = LegalCompliance.compliant;
            recordLegal.setStatus(status);
            Set<String> otherRelevantDataCountries = new HashSet<>(Collections.singletonList("BR"));
            recordLegal.setOtherRelevantDataCountries(otherRelevantDataCountries);
            recordMetadata.setLegal(recordLegal);

            RecordState recordStatus = RecordState.active;
            recordMetadata.setStatus(recordStatus);

            recordMetadata.setUser(user);
            return recordMetadata;
        }).collect(Collectors.toList());
    }
}
