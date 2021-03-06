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

package com.liferay.social.activity.customizer.interpreter;

import com.liferay.asset.kernel.AssetRendererFactoryRegistryUtil;
import com.liferay.asset.kernel.model.AssetEntry;
import com.liferay.asset.kernel.model.AssetRenderer;
import com.liferay.asset.kernel.model.AssetRendererFactory;
import com.liferay.asset.kernel.model.AssetTag;
import com.liferay.asset.kernel.service.AssetEntryLocalService;
import com.liferay.petra.string.StringPool;
import com.liferay.portal.kernel.exception.NoSuchModelException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.portlet.LiferayPortletResponse;
import com.liferay.portal.kernel.portletfilerepository.PortletFileRepositoryUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.LayoutLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.UserLocalService;
import com.liferay.portal.kernel.util.DateUtil;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Http;
import com.liferay.portal.kernel.util.ResourceBundleLoader;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.URLCodec;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.social.kernel.model.BaseSocialActivityInterpreter;
import com.liferay.social.kernel.model.SocialActivity;
import com.liferay.social.kernel.model.SocialActivityConstants;
import com.liferay.social.kernel.model.SocialActivityInterpreter;
import com.liferay.wiki.constants.WikiPortletKeys;
import com.liferay.wiki.model.WikiPage;
import com.liferay.wiki.model.WikiPageResource;
import com.liferay.wiki.service.WikiPageLocalService;
import com.liferay.wiki.service.WikiPageResourceLocalService;
import com.liferay.wiki.social.WikiActivityKeys;

import java.text.DateFormat;

import java.util.Date;
import java.util.List;

import javax.portlet.PortletRequest;
import javax.portlet.PortletURL;
import javax.portlet.WindowState;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

/**
 * @author Istvan Sajtos
 */
@Component(
	immediate = true,
	property = {
		"javax.portlet.name=" + WikiPortletKeys.WIKI,
		"service.ranking:Integer=100"
	},
	service = SocialActivityInterpreter.class
)
public class CustomWikiActivityInterpreter
	extends BaseSocialActivityInterpreter {

	@Override
	public String[] getClassNames() {
		return _CLASS_NAMES;
	}

	@Override
	public String getSelector() {
		return _SELECTOR;
	}

	@Override
	protected String addNoSuchEntryRedirect(
			String url, String className, long classPK,
			ServiceContext serviceContext)
		throws Exception {

		LiferayPortletResponse liferayPortletResponse =
			serviceContext.getLiferayPortletResponse();

		if (liferayPortletResponse == null) {
			return null;
		}

		// Share page plid

		long plid = 34778;

		WikiPage page = _wikiPageLocalService.getPage(classPK);

		PortletURL portletURL = liferayPortletResponse.createLiferayPortletURL(
			plid, WikiPortletKeys.WIKI, PortletRequest.RENDER_PHASE);

		portletURL.setParameter("mvcRenderCommandName", "/wiki/view");
		portletURL.setParameter("nodeId", String.valueOf(page.getNodeId()));
		portletURL.setParameter("title", page.getTitle());
		portletURL.setWindowState(WindowState.MAXIMIZED);

		String viewEntryURL = portletURL.toString();

		if (Validator.isNotNull(viewEntryURL)) {
			return viewEntryURL;
		}

		return _http.setParameter(url, "noSuchEntryRedirect", viewEntryURL);
	}

	protected String getAttachmentTitle(
			SocialActivity activity, WikiPageResource pageResource,
			ServiceContext serviceContext)
		throws Exception {

		int activityType = activity.getType();

		if ((activityType == SocialActivityConstants.TYPE_ADD_ATTACHMENT) ||
			(activityType ==
				SocialActivityConstants.TYPE_MOVE_ATTACHMENT_TO_TRASH) ||
			(activityType ==
				SocialActivityConstants.TYPE_RESTORE_ATTACHMENT_FROM_TRASH)) {

			String link = null;

			FileEntry fileEntry = null;

			try {
				long fileEntryId = GetterUtil.getLong(
					activity.getExtraDataValue("fileEntryId"));

				fileEntry = PortletFileRepositoryUtil.getPortletFileEntry(
					fileEntryId);
			}
			catch (NoSuchModelException nsme) {

				// LPS-52675

				if (_log.isDebugEnabled()) {
					_log.debug(nsme, nsme);
				}
			}

			String fileEntryTitle = activity.getExtraDataValue(
				"fileEntryTitle");

			if ((fileEntry != null) && !fileEntry.isInTrash()) {
				StringBundler sb = new StringBundler(9);

				sb.append(serviceContext.getPathMain());
				sb.append("/wiki/get_page_attachment?p_l_id=");
				sb.append(serviceContext.getPlid());
				sb.append("&nodeId=");
				sb.append(pageResource.getNodeId());
				sb.append("&title=");
				sb.append(URLCodec.encodeURL(pageResource.getTitle()));
				sb.append("&fileName=");
				sb.append(fileEntryTitle);

				link = sb.toString();
			}

			return wrapLink(link, fileEntryTitle);
		}

		return StringPool.BLANK;
	}

	@Override
	protected String getBody(
			SocialActivity activity, ServiceContext serviceContext)
		throws Exception {

		// Date

		Date createDate = DateUtil.newDate(activity.getCreateDate());

		// View count

		WikiPageResource pageResource =
			_wikiPageResourceLocalService.getWikiPageResource(
				activity.getClassPK());

		AssetEntry assetEntry = _assetEntryLocalService.fetchEntry(
			WikiPage.class.getName(), pageResource.getPrimaryKey());

		int viewCount = assetEntry.getViewCount();

		// Excerpt

		String summary = StringPool.BLANK;

		try {
			AssetRendererFactory<?> assetRendererFactory =
				AssetRendererFactoryRegistryUtil.
					getAssetRendererFactoryByClassName(
						WikiPage.class.getName());

			AssetRenderer<?> assetRenderer =
				assetRendererFactory.getAssetRenderer(activity.getClassPK());

			summary = assetRenderer.getSearchSummary(
				serviceContext.getLocale());
		}
		catch (Exception e) {
			_log.error("Cannot create summary", e);
		}

		// Tags

		List<AssetTag> tags = assetEntry.getTags();

		return _getFormattedBody(
			createDate, viewCount, summary, tags, serviceContext);
	}

	@Override
	protected String getPath(
		SocialActivity activity, ServiceContext serviceContext) {

		return "/wiki/find_page?pageResourcePrimKey=" + activity.getClassPK();
	}

	@Override
	protected ResourceBundleLoader getResourceBundleLoader() {
		return _resourceBundleLoader;
	}

	@Override
	protected String getTitle(
			SocialActivity activity, ServiceContext serviceContext)
		throws Exception {

		String entryTitle = getEntryTitle(activity, serviceContext);

		String link = getLink(activity, serviceContext);

		Object[] titleArguments = getTitleArguments(
			null, activity, link, entryTitle, serviceContext);

		String titlePattern = "<h5><strong>{0}\t{1}</strong></h5>";

		return serviceContext.translate(titlePattern, titleArguments);
	}

	@Override
	protected Object[] getTitleArguments(
			String groupName, SocialActivity activity, String link,
			String title, ServiceContext serviceContext)
		throws Exception {

		WikiPageResource pageResource =
			_wikiPageResourceLocalService.fetchWikiPageResource(
				activity.getClassPK());

		if (pageResource == null) {
			return null;
		}

		String activityText =
			"<font color=\"black\">" + _getActivityText(activity) + "</font>";

		title = wrapLink(link, title);

		return new Object[] {activityText, title};
	}

	@Override
	protected String getTitlePattern(
		String groupName, SocialActivity activity) {

		int activityType = activity.getType();

		if ((activityType == WikiActivityKeys.ADD_COMMENT) ||
			(activityType == SocialActivityConstants.TYPE_ADD_COMMENT)) {

			if (Validator.isNull(groupName)) {
				return "activity-wiki-page-add-comment";
			}

			return "activity-wiki-page-add-comment-in";
		}
		else if (activityType == WikiActivityKeys.ADD_PAGE) {
			if (Validator.isNull(groupName)) {
				return "activity-wiki-page-add-page";
			}

			return "activity-wiki-page-add-page-in";
		}
		else if (activityType == SocialActivityConstants.TYPE_ADD_ATTACHMENT) {
			if (Validator.isNull(groupName)) {
				return "activity-wiki-page-add-attachment";
			}

			return "activity-wiki-page-add-attachment-in";
		}
		else if (activityType ==
					SocialActivityConstants.TYPE_MOVE_ATTACHMENT_TO_TRASH) {

			if (Validator.isNull(groupName)) {
				return "activity-wiki-page-remove-attachment";
			}

			return "activity-wiki-page-remove-attachment-in";
		}
		else if (activityType ==
					SocialActivityConstants.
						TYPE_RESTORE_ATTACHMENT_FROM_TRASH) {

			if (Validator.isNull(groupName)) {
				return "activity-wiki-page-restore-attachment";
			}

			return "activity-wiki-page-restore-attachment-in";
		}
		else if (activityType == SocialActivityConstants.TYPE_MOVE_TO_TRASH) {
			if (Validator.isNull(groupName)) {
				return "activity-wiki-page-move-to-trash";
			}

			return "activity-wiki-page-move-to-trash-in";
		}
		else if (activityType ==
					SocialActivityConstants.TYPE_RESTORE_FROM_TRASH) {

			if (Validator.isNull(groupName)) {
				return "activity-wiki-page-restore-from-trash";
			}

			return "activity-wiki-page-restore-from-trash-in";
		}
		else if (activityType == WikiActivityKeys.UPDATE_PAGE) {
			if (Validator.isNull(groupName)) {
				return "activity-wiki-page-update-page";
			}

			return "activity-wiki-page-update-page-in";
		}

		return null;
	}

	@Override
	protected boolean hasPermissions(
			PermissionChecker permissionChecker, SocialActivity activity,
			String actionId, ServiceContext serviceContext)
		throws Exception {

		if (!_wikiPageModelResourcePermission.contains(
				permissionChecker, activity.getClassPK(), ActionKeys.VIEW)) {

			return false;
		}

		int activityType = activity.getType();

		if (activityType == WikiActivityKeys.UPDATE_PAGE) {
			WikiPageResource pageResource =
				_wikiPageResourceLocalService.getPageResource(
					activity.getClassPK());

			double version = GetterUtil.getDouble(
				activity.getExtraDataValue("version"));

			WikiPage page = _wikiPageLocalService.getPage(
				pageResource.getNodeId(), pageResource.getTitle(), version);

			if (!page.isApproved() &&
				!_wikiPageModelResourcePermission.contains(
					permissionChecker, activity.getClassPK(),
					ActionKeys.UPDATE)) {

				return false;
			}
		}

		return true;
	}

	@Reference(unbind = "-")
	protected void setAssetEntryLocalService(
		AssetEntryLocalService assetEntryLocalService) {

		_assetEntryLocalService = assetEntryLocalService;
	}

	@Reference(unbind = "-")
	protected void setGroupLocalService(GroupLocalService groupLocalService) {
		_groupLocalService = groupLocalService;
	}

	@Reference(unbind = "-")
	protected void setLayoutLocalService(
		LayoutLocalService layoutLocalService) {

		_layoutLocalService = layoutLocalService;
	}

	@Reference(unbind = "-")
	protected void setUserLocalService(UserLocalService userLocalService) {
		_userLocalService = userLocalService;
	}

	@Reference(unbind = "-")
	protected void setWikiPageLocalService(
		WikiPageLocalService wikiPageLocalService) {

		_wikiPageLocalService = wikiPageLocalService;
	}

	@Reference(unbind = "-")
	protected void setWikiPageResourceLocalService(
		WikiPageResourceLocalService wikiPageResourceLocalService) {

		_wikiPageResourceLocalService = wikiPageResourceLocalService;
	}

	protected String wrapTagLink(String link, String text) {
		StringBundler sb = new StringBundler(5);

		sb.append("<a class=\"badge badge-secondary\" href=\"");
		sb.append(link);
		sb.append("\">");
		sb.append(text);
		sb.append("</a>");

		return sb.toString();
	}

	private String _getActivityText(SocialActivity activity) {
		int activityType = activity.getType();

		String activityText = null;

		if ((activityType == WikiActivityKeys.ADD_COMMENT) ||
			(activityType == SocialActivityConstants.TYPE_ADD_COMMENT)) {

			activityText = "Commented";
		}
		else if (activityType == WikiActivityKeys.ADD_PAGE) {
			activityText = "Created";
		}
		else if (activityType == SocialActivityConstants.TYPE_MOVE_TO_TRASH) {
			activityText = "Removed";
		}
		else if (activityType ==
					SocialActivityConstants.TYPE_RESTORE_FROM_TRASH) {

			activityText = "Restored";
		}
		else {
			activityText = "Updated";
		}

		return activityText;
	}

	private String _getFormattedBody(
			Date date, int viewCount, String summary, List<AssetTag> tags,
			ServiceContext serviceContext)
		throws PortalException {

		DateFormat df = DateFormat.getDateTimeInstance(
			DateFormat.MEDIUM, DateFormat.SHORT, serviceContext.getLocale());

		String dateString = df.format(date);

		String tagRow = StringPool.BLANK;

		for (AssetTag tag : tags) {
			String url =
				"https://grow.liferay.com/share/-/wiki/tag/" + tag.getName();

			if (Validator.isNotNull(tagRow)) {
				tagRow = tagRow.concat(StringPool.SPACE);
			}

			tagRow = tagRow.concat(wrapTagLink(url, tag.getName()));
		}

		StringBundler sb = new StringBundler(19);

		sb.append("<h6 class=\"text-default\">");
		sb.append("<p style=\"text-align:left;\">");
		sb.append(dateString);
		sb.append("</h6>");
		sb.append("<h6 class=\"text-default\">");
		sb.append("<span style=\"float:right;\">");
		sb.append(String.valueOf(viewCount));
		sb.append(" views");
		sb.append("</span>");
		sb.append("</p>");
		sb.append("</h6>");
		sb.append("<h6 class=\"text-default\">");
		sb.append(StringUtil.shorten(summary, 200));
		sb.append("</h6>");
		sb.append("<h6 class=\"text-default\">");
		sb.append("<span class=\"taglib-asset-tags-summary\">");
		sb.append(tagRow);
		sb.append("</span>");
		sb.append("</h6>");

		return sb.toString();
	}

	private static final String[] _CLASS_NAMES = {WikiPage.class.getName()};

	private static final String _SELECTOR = "CUSTOM";

	private static final Log _log = LogFactoryUtil.getLog(
		CustomWikiActivityInterpreter.class);

	private AssetEntryLocalService _assetEntryLocalService;
	private GroupLocalService _groupLocalService;

	@Reference
	private Http _http;

	private LayoutLocalService _layoutLocalService;

	@Reference(
		policy = ReferencePolicy.DYNAMIC,
		policyOption = ReferencePolicyOption.GREEDY,
		target = "(bundle.symbolic.name=com.liferay.wiki.web)"
	)
	private volatile ResourceBundleLoader _resourceBundleLoader;

	private UserLocalService _userLocalService;
	private WikiPageLocalService _wikiPageLocalService;

	@Reference(target = "(model.class.name=com.liferay.wiki.model.WikiPage)")
	private ModelResourcePermission<WikiPage> _wikiPageModelResourcePermission;

	private WikiPageResourceLocalService _wikiPageResourceLocalService;

}