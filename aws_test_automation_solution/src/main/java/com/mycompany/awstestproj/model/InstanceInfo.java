package com.mycompany.awstestproj.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InstanceInfo {
    private String availability_zone;
    private String private_ipv4;
    private String region;


}
