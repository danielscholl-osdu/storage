package org.opengroup.osdu.storage.model.policy;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class User {
    private String email;

    private String permenantLocation;

    private String nationality;
}
