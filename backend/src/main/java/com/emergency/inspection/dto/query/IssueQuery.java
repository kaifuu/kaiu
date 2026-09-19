package com.emergency.inspection.dto.query;

import com.emergency.inspection.common.PageQuery;
import com.emergency.inspection.entity.InspectIssue;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 问题清单分页查询:keyword 模糊标题/地点/设备 */
@Data
@EqualsAndHashCode(callSuper = true)
public class IssueQuery extends PageQuery {

    private InspectIssue.IssueType issueType;
    private InspectIssue.Status status;
    private InspectIssue.Source source;
    private String dept;
}
