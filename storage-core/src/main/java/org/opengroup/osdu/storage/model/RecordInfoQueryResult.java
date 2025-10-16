package org.opengroup.osdu.storage.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordInfoQueryResult <T> {

    private String cursor;

    private List<T> results;
}
