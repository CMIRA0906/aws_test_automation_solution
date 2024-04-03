package com.mycompany.awstestproj.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CustomPolicy {

    private String policyName;
    private String actions;
    private String resources;
    private String effect;

}
