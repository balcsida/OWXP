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

package badgeimport.portlet;

import badgeimport.constants.BadgeImportPortletKeys;

import com.liferay.counter.kernel.service.CounterLocalServiceUtil;
import com.liferay.grow.gamification.model.Badge;
import com.liferay.grow.gamification.service.BadgeLocalServiceUtil;
import com.liferay.grow.gamification.service.LDateLocalServiceUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.security.auth.CompanyThreadLocal;
import com.liferay.portal.kernel.service.UserLocalServiceUtil;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;

import org.osgi.service.component.annotations.Component;

/**
 * @author István András Dézsi
 */
@Component(
	immediate = true,
	property = {
		"com.liferay.portlet.display-category=category.sample",
		"com.liferay.portlet.instanceable=true",
		"javax.portlet.display-name=BadgeImport Portlet",
		"javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/view.jsp",
		"javax.portlet.name=" + BadgeImportPortletKeys.BADGE_IMPORT,
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=power-user,user"
	},
	service = Portlet.class
)
public class BadgeImportPortlet extends MVCPortlet {

	public void importBadges(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ThemeDisplay themeDisplay = (ThemeDisplay)actionRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		_importFromCSV("Loyalty_Badge_input.csv", themeDisplay);
		_importFromCSV("1st_grow_article_badge_input.csv", themeDisplay);
	}

	private InputStream _getStream(String fileName) throws Exception {
		Class<?> clazz = getClass();

		return clazz.getResourceAsStream("dependencies/" + fileName);
	}

	private void _importFromCSV(String fileName, ThemeDisplay themeDisplay)
		throws Exception {

		long companyId = CompanyThreadLocal.getCompanyId();

		User fromUser = themeDisplay.getUser();

		long fromUserId = fromUser.getUserId();
		String fromUserName = fromUser.getFullName();

		Date now = new Date();
		Calendar cal = Calendar.getInstance();

		cal.setTime(now);

		long dateId = LDateLocalServiceUtil.getDateId(
			cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1,
			cal.get(Calendar.DAY_OF_MONTH));

		String userEmailAddress = StringPool.BLANK;
		String description = StringPool.BLANK;

		InputStream inputStream = null;
		BufferedReader bufferedReader = null;

		User user = null;

		try {
			inputStream = _getStream(fileName);

			bufferedReader = new BufferedReader(
				new InputStreamReader(inputStream));

			String line = StringPool.BLANK;

			// Reading the header row

			line = bufferedReader.readLine();

			while ((line = bufferedReader.readLine()) != null) {
				try {
					String[] fields = StringUtil.split(
						line, StringPool.SEMICOLON);

					if (fields.length != 2) {
						continue;
					}

					userEmailAddress = fields[0];
					description = fields[1];

					user = UserLocalServiceUtil.getUserByEmailAddress(
						companyId, userEmailAddress);

					long badgeId = CounterLocalServiceUtil.increment(
						Badge.class.getName());

					Badge badge = BadgeLocalServiceUtil.createBadge(badgeId);

					badge.setUserId(fromUserId);
					badge.setAssignedDateId(dateId);
					badge.setBadgeTypeId(_LOYALTY);
					badge.setCompanyId(companyId);
					badge.setCreateDate(now);
					badge.setDescription(description);
					badge.setGroupId(user.getGroupId());
					badge.setToUserId(user.getUserId());
					badge.setUserName(fromUserName);
					badge.setUuid(String.valueOf(UUID.randomUUID()));

					BadgeLocalServiceUtil.addBadge(badge, false);
				}
				catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}
		}
		finally {
			if (inputStream != null) {
				try {
					inputStream.close();
				}
				catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}

			if (bufferedReader != null) {
				try {
					bufferedReader.close();
				}
				catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
		}
	}

	private static final int _LOYALTY = 3;

}