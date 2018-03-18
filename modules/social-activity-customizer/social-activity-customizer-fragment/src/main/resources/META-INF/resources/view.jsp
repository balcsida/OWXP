<%--
/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
--%>

<%@ include file="/init.jsp" %>

<%
ServiceContext serviceContext = ServiceContextFactory.getInstance(request);

Group scopeGroup = serviceContext.getScopeGroup();

if (scopeGroup.isUser()) {
	CustomSocialActivitiesQueryHelper customSocialActivitiesQueryHelper = (CustomSocialActivitiesQueryHelper)request.getAttribute(SocialActivitiesWebKeys.SOCIAL_ACTIVITIES_QUERY_HELPER);

	customSocialActivitiesQueryHelper.setTypes(null);

	socialActivitiesDisplayContext = new DefaultSocialActivitiesDisplayContext(socialActivitiesRequestHelper, customSocialActivitiesQueryHelper);

	String activityType = ParamUtil.getString(request, "activityType");

	if (!activityType.equals(StringPool.BLANK) && !activityType.equals("ALL")) {
		long[] types = null;

		switch (activityType) {
			case "CREATED":
				types = new long[] {WikiActivityKeys.ADD_PAGE};
				break;
			case "COMMENTED":
				types = new long[] {WikiActivityKeys.ADD_COMMENT,
					SocialActivityConstants.TYPE_ADD_COMMENT};
				break;
			case "UPDATED":
				types = new long[] {WikiActivityKeys.UPDATE_PAGE,
					SocialActivityConstants.TYPE_MOVE_TO_TRASH,
					SocialActivityConstants.TYPE_RESTORE_FROM_TRASH,
					SocialActivityConstants.TYPE_MOVE_ATTACHMENT_TO_TRASH,
					SocialActivityConstants.TYPE_RESTORE_ATTACHMENT_FROM_TRASH,
					SocialActivityConstants.TYPE_ADD_ATTACHMENT};
				break;
		}

		customSocialActivitiesQueryHelper.setTypes(types);
	}
}
%>

<c:if test="<%= socialActivitiesDisplayContext.isTabsVisible() %>">
	<liferay-ui:tabs
		names="<%= socialActivitiesDisplayContext.getTabsNames() %>"
		type="tabs nav-tabs-default"
		url="<%= socialActivitiesDisplayContext.getTabsURL() %>"
		value="<%= socialActivitiesDisplayContext.getSelectedTabName() %>"
	/>
</c:if>

<liferay-ui:social-activities
	activitySets="<%= socialActivitiesDisplayContext.getSocialActivitySets() %>"
	feedDisplayStyle="<%= socialActivitiesDisplayContext.getRSSDisplayStyle() %>"
	feedEnabled="<%= false %>"
	feedResourceURL="<%= socialActivitiesDisplayContext.getRSSResourceURL() %>"
	feedTitle="<%= socialActivitiesDisplayContext.getTaglibFeedTitle() %>"
	feedType="<%= socialActivitiesDisplayContext.getRSSFeedType() %>"
	feedURLMessage="<%= socialActivitiesDisplayContext.getTaglibFeedTitle() %>"
/>