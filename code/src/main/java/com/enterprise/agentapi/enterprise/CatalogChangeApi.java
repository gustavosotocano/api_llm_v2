package com.enterprise.agentapi.enterprise;

import com.enterprise.agentapi.domain.CatalogChangeResponse;
import com.enterprise.agentapi.domain.CatalogProposalType;

import java.util.List;

public interface CatalogChangeApi {
    CatalogChangeResponse propose(CatalogProposalType proposalType,
                                  String categoryCode,
                                  List<String> merchants,
                                  String reason,
                                  String agentSessionId,
                                  String userId);
}
