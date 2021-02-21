package org.opengroup.osdu.storage.model.policy;

import com.google.api.client.util.Strings;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.opengroup.osdu.core.common.model.storage.Record;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyInput {
    private Enum operation;

    private List<String> groups;

    private Record record;

    private Set<String> legalTags;

    private Set<String> otherRelevantDataCountries;

    public void setGroups(String groups) {
        this.groups = Strings.isNullOrEmpty(groups) ? new ArrayList<>() : Arrays.asList(groups.split(","));
    }
}
