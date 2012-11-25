/* Copyright (c) 2012, University of Edinburgh.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice, this
 *   list of conditions and the following disclaimer in the documentation and/or
 *   other materials provided with the distribution.
 *
 * * Neither the name of the University of Edinburgh nor the names of its
 *   contributors may be used to endorse or promote products derived from this
 *   software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 *
 * This software is derived from (and contains code from) QTItools and MathAssessEngine.
 * QTItools is (c) 2008, University of Southampton.
 * MathAssessEngine is (c) 2010, University of Edinburgh.
 */
package uk.ac.ed.ph.qtiworks.services.candidate;

import uk.ac.ed.ph.qtiworks.QtiWorksLogicException;
import uk.ac.ed.ph.qtiworks.QtiWorksRuntimeException;
import uk.ac.ed.ph.qtiworks.domain.DomainEntityNotFoundException;
import uk.ac.ed.ph.qtiworks.domain.IdentityContext;
import uk.ac.ed.ph.qtiworks.domain.RequestTimestampContext;
import uk.ac.ed.ph.qtiworks.domain.dao.CandidateEventDao;
import uk.ac.ed.ph.qtiworks.domain.dao.CandidateResponseDao;
import uk.ac.ed.ph.qtiworks.domain.dao.CandidateSessionDao;
import uk.ac.ed.ph.qtiworks.domain.entities.AssessmentPackage;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateEvent;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateEventCategory;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateEventNotification;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateFileSubmission;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateItemEventType;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateResponse;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateSession;
import uk.ac.ed.ph.qtiworks.domain.entities.Delivery;
import uk.ac.ed.ph.qtiworks.domain.entities.DeliverySettings;
import uk.ac.ed.ph.qtiworks.domain.entities.ItemDeliverySettings;
import uk.ac.ed.ph.qtiworks.domain.entities.ResponseLegality;
import uk.ac.ed.ph.qtiworks.rendering.AbstractRenderingRequest;
import uk.ac.ed.ph.qtiworks.rendering.AssessmentRenderer;
import uk.ac.ed.ph.qtiworks.rendering.RenderingMode;
import uk.ac.ed.ph.qtiworks.rendering.RenderingOptions;
import uk.ac.ed.ph.qtiworks.rendering.StandaloneItemRenderingRequest;
import uk.ac.ed.ph.qtiworks.rendering.TerminatedRenderingRequest;
import uk.ac.ed.ph.qtiworks.services.AssessmentPackageFileService;
import uk.ac.ed.ph.qtiworks.services.CandidateAuditLogger;
import uk.ac.ed.ph.qtiworks.services.CandidateDataServices;
import uk.ac.ed.ph.qtiworks.services.CandidateSessionStarter;
import uk.ac.ed.ph.qtiworks.services.EntityGraphService;
import uk.ac.ed.ph.qtiworks.services.FilespaceManager;
import uk.ac.ed.ph.qtiworks.services.domain.OutputStreamer;

import uk.ac.ed.ph.jqtiplus.internal.util.Assert;
import uk.ac.ed.ph.jqtiplus.node.AssessmentObjectType;
import uk.ac.ed.ph.jqtiplus.node.item.AssessmentItem;
import uk.ac.ed.ph.jqtiplus.node.result.AssessmentResult;
import uk.ac.ed.ph.jqtiplus.notification.NotificationLevel;
import uk.ac.ed.ph.jqtiplus.notification.NotificationRecorder;
import uk.ac.ed.ph.jqtiplus.running.ItemSessionController;
import uk.ac.ed.ph.jqtiplus.serialization.QtiSerializer;
import uk.ac.ed.ph.jqtiplus.state.ItemSessionState;
import uk.ac.ed.ph.jqtiplus.types.FileResponseData;
import uk.ac.ed.ph.jqtiplus.types.Identifier;
import uk.ac.ed.ph.jqtiplus.types.ResponseData;
import uk.ac.ed.ph.jqtiplus.types.StringResponseData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Resource;

import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service the manages the real-time delivery of a standalone {@link AssessmentItem}
 * to a candidate.
 * <p>
 * NOTE: Current single item delivery assumes that items are always presented immediately after
 * template processing runs, and that attempts are always treated as being submitted.
 * <p>
 * NOTE: Remember there is no {@link IdentityContext} for candidates.
 *
 * @author David McKain
 *
 * @see CandidateSessionStarter
 */
@Service
@Transactional(propagation=Propagation.REQUIRED)
public class CandidateItemDeliveryService {

    @Resource
    private RequestTimestampContext requestTimestampContext;

    @Resource
    private CandidateAuditLogger candidateAuditLogger;

    @Resource
    private QtiSerializer qtiSerializer;

    @Resource
    private EntityGraphService entityGraphService;

    @Resource
    private AssessmentPackageFileService assessmentPackageFileService;

    @Resource
    private FilespaceManager filespaceManager;

    @Resource
    private CandidateDataServices candidateDataServices;

    @Resource
    private AssessmentRenderer assessmentRenderer;

    @Resource
    private CandidateUploadService candidateUploadService;

    @Resource
    private CandidateSessionDao candidateSessionDao;

    @Resource
    private CandidateEventDao candidateEventDao;

    @Resource
    private CandidateResponseDao candidateResponseDao;

    //----------------------------------------------------
    // Session access

    /**
     * Looks up the {@link CandidateSession} having the given ID (xid)
     * and checks the given sessionToken against that stored in the session as a means of
     * "authentication".
     *
     * @param xid
     * @return
     * @throws DomainEntityNotFoundException
     * @throws CandidateForbiddenException
     * @throws CandidateCandidatePrivilegeException
     */
    public CandidateSession lookupCandidateSession(final long xid, final String sessionToken)
            throws DomainEntityNotFoundException, CandidateForbiddenException {
        Assert.notNull(sessionToken, "sessionToken");
        final CandidateSession candidateSession = candidateSessionDao.requireFindById(xid);
        if (!sessionToken.equals(candidateSession.getSessionToken())) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.ACCESS_CANDIDATE_SESSION);
        }
        if (candidateSession.getDelivery().getAssessment().getAssessmentType()!=AssessmentObjectType.ASSESSMENT_ITEM) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.ACCESS_CANDIDATE_SESSION_AS_ITEM);
        }
        return candidateSession;
    }

    private void ensureSessionNotTerminated(final CandidateSession candidateSession) throws CandidateForbiddenException {
        if (candidateSession.isTerminated()) {
            /* No access when session has been is closed */
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.ACCESS_TERMINATED_SESSION);
        }
    }

    //----------------------------------------------------
    // Rendering

    /**
     * Renders the current state of the {@link CandidateSession} having
     * the given ID (xid).
     */
    public void renderCurrentState(final long xid, final String sessionToken,
            final RenderingOptions renderingOptions, final OutputStreamer outputStreamer)
            throws CandidateForbiddenException, DomainEntityNotFoundException, IOException {
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        renderCurrentState(candidateSession, renderingOptions, outputStreamer);
    }

    public void renderCurrentState(final CandidateSession candidateSession,
            final RenderingOptions renderingOptions,
            final OutputStreamer outputStreamer) throws IOException {
        Assert.notNull(candidateSession, "candidateSession");
        Assert.notNull(renderingOptions, "renderingOptions");
        Assert.notNull(outputStreamer, "outputStreamer");

        /* Look up most recent event */
        final CandidateEvent latestEvent = candidateDataServices.getMostRecentItemEvent(candidateSession);

        /* Create temporary file to hold the output before it gets streamed */
        final File resultFile = filespaceManager.createTempFile();
        try {
            /* Render to temp file */
            FileOutputStream resultOutputStream = null;
            try {
                resultOutputStream = new FileOutputStream(resultFile);
                renderEvent(candidateSession, latestEvent, renderingOptions, resultOutputStream);
            }
            catch (final IOException e) {
                throw new QtiWorksRuntimeException("Unexpected IOException", e);
            }
            finally {
                IOUtils.closeQuietly(resultOutputStream);
            }

            /* Finally stream to caller */
            final String contentType = renderingOptions.getSerializationMethod().getContentType();
            final long contentLength = resultFile.length();
            FileInputStream resultInputStream = null;
            try {
                resultInputStream = new FileInputStream(resultFile);
                outputStreamer.stream(contentType, contentLength, requestTimestampContext.getCurrentRequestTimestamp(),
                        resultInputStream);
            }
            catch (final FileNotFoundException e) {
                throw new QtiWorksRuntimeException("Unexpected IOException", e);
            }
            catch (final IOException e) {
                /* Streamer threw Exception */
                throw e;
            }
            finally {
                IOUtils.closeQuietly(resultInputStream);
            }
        }
        finally {
            if (!resultFile.delete()) {
                throw new QtiWorksRuntimeException("Could not delete result file " + resultFile.getPath());
            }
        }
    }

    private void renderEvent(final CandidateSession candidateSession,
            final CandidateEvent candidateEvent,
            final RenderingOptions renderingOptions, final OutputStream resultStream) {
        final ItemSessionState itemSessionState = candidateDataServices.loadItemSessionState(candidateEvent);
        if (candidateSession.isTerminated()) {
            /* Session is terminated */
            renderTerminated(candidateEvent, renderingOptions, resultStream);
        }
        else if (itemSessionState.isClosed()) {
            /* Item is closed */
            renderEventWhenClosed(candidateEvent, itemSessionState, renderingOptions, resultStream);
        }
        else {
            /* Interacting */
            renderEventWhenInteracting(candidateEvent, itemSessionState, renderingOptions, resultStream);
        }
    }

    private void renderEventWhenInteracting(final CandidateEvent candidateEvent,
            final ItemSessionState itemSessionState, final RenderingOptions renderingOptions,
            final OutputStream resultStream) {
        final CandidateItemEventType eventType = candidateEvent.getItemEventType();
        switch (eventType) {
            case INIT:
            case REINIT:
            case RESET:
                renderWhenInteracting(candidateEvent, itemSessionState, renderingOptions,
                        RenderingMode.AFTER_INITIALISATION, resultStream);
                break;

            case ATTEMPT_VALID:
            case ATTEMPT_INVALID:
            case ATTEMPT_BAD:
                renderWhenInteracting(candidateEvent, itemSessionState, renderingOptions,
                        RenderingMode.AFTER_ATTEMPT, resultStream);
                break;

            default:
                throw new QtiWorksLogicException("Unexpected logic branch. Event type " + eventType);
        }
    }

    private void renderWhenInteracting(final CandidateEvent candidateEvent,
            final ItemSessionState itemSessionState, final RenderingOptions renderingOptions,
            final RenderingMode renderingMode, final OutputStream resultStream) {
        final CandidateSession candidateSession = candidateEvent.getCandidateSession();
        final Delivery delivery = candidateSession.getDelivery();
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) delivery.getDeliverySettings();

        /* Update current value for 'duration' */
        final double duration = computeItemSessionDuration(candidateSession);
        itemSessionState.setDuration(duration);

        /* Initialise rendering request */
        final StandaloneItemRenderingRequest renderingRequest = initItemRenderingRequest(candidateEvent,
                itemSessionState, renderingOptions, renderingMode);
        renderingRequest.setCloseAllowed(itemDeliverySettings.isAllowClose());
        renderingRequest.setReinitAllowed(itemDeliverySettings.isAllowReinitWhenInteracting());
        renderingRequest.setResetAllowed(itemDeliverySettings.isAllowResetWhenInteracting());
        renderingRequest.setSolutionAllowed(itemDeliverySettings.isAllowSolutionWhenInteracting());
        renderingRequest.setResultAllowed(false);
        renderingRequest.setSourceAllowed(itemDeliverySettings.isAllowSource());

        /* Pass to rendering layer */
        doRendering(candidateEvent, renderingRequest, resultStream);
    }

    private void renderEventWhenClosed(final CandidateEvent candidateEvent,
            final ItemSessionState itemSessionState, final RenderingOptions renderingOptions,
            final OutputStream resultStream) {
        final CandidateItemEventType eventType = candidateEvent.getItemEventType();
        switch (eventType) {
            case ATTEMPT_VALID:
            case ATTEMPT_INVALID:
            case ATTEMPT_BAD:
                renderWhenClosed(candidateEvent, itemSessionState, renderingOptions,
                        RenderingMode.AFTER_ATTEMPT, resultStream);
                break;

            case INIT:
            case REINIT:
            case RESET:
            case CLOSE:
                renderWhenClosed(candidateEvent, itemSessionState, renderingOptions,
                        RenderingMode.CLOSED, resultStream);
                break;

            case SOLUTION:
                renderWhenClosed(candidateEvent, itemSessionState, renderingOptions,
                        RenderingMode.SOLUTION, resultStream);
                break;

            case PLAYBACK:
                renderPlayback(candidateEvent, renderingOptions, resultStream);
                break;

            default:
                throw new QtiWorksLogicException("Unexpected logic branch. Event type " + eventType);
        }
    }

    private void renderWhenClosed(final CandidateEvent candidateEvent,
            final ItemSessionState itemSessionState, final RenderingOptions renderingOptions,
            final RenderingMode renderingMode, final OutputStream resultStream) {
        final StandaloneItemRenderingRequest renderingRequest = initItemRenderingRequestWhenClosed(candidateEvent,
                itemSessionState, renderingOptions, renderingMode);
        doRendering(candidateEvent, renderingRequest, resultStream);
    }

    private void renderPlayback(final CandidateEvent candidateEvent,
            final RenderingOptions renderingOptions, final OutputStream resultStream) {
        final CandidateEvent playbackEvent = candidateEvent.getPlaybackEvent();
        final ItemSessionState playbackItemSessionState = candidateDataServices.loadItemSessionState(playbackEvent);
        final StandaloneItemRenderingRequest renderingRequest = initItemRenderingRequestWhenClosed(playbackEvent,
                playbackItemSessionState, renderingOptions, RenderingMode.PLAYBACK);

        /* Record which event we're playing back */
        renderingRequest.setCurrentPlaybackEvent(playbackEvent);

        /* Do rendering */
        doRendering(candidateEvent, renderingRequest, resultStream);
    }

    private StandaloneItemRenderingRequest initItemRenderingRequestWhenClosed(final CandidateEvent candidateEvent,
            final ItemSessionState itemSessionState, final RenderingOptions renderingOptions,
            final RenderingMode renderingMode) {
        final CandidateSession candidateSession = candidateEvent.getCandidateSession();
        final Delivery delivery = candidateSession.getDelivery();
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) delivery.getDeliverySettings();

        final StandaloneItemRenderingRequest renderingRequest = initItemRenderingRequest(candidateEvent,
                itemSessionState, renderingOptions, renderingMode);
        renderingRequest.setCloseAllowed(false);
        renderingRequest.setSolutionAllowed(itemDeliverySettings.isAllowSolutionWhenClosed());
        renderingRequest.setReinitAllowed(itemDeliverySettings.isAllowReinitWhenClosed());
        renderingRequest.setResetAllowed(itemDeliverySettings.isAllowResetWhenClosed());
        renderingRequest.setResultAllowed(itemDeliverySettings.isAllowResult());
        renderingRequest.setSourceAllowed(itemDeliverySettings.isAllowSource());

        renderingRequest.setPlaybackAllowed(itemDeliverySettings.isAllowPlayback());
        if (itemDeliverySettings.isAllowPlayback()) {
            renderingRequest.setPlaybackEvents(getPlaybackEvents(candidateSession));
        }
        return renderingRequest;
    }

    private void renderTerminated(final CandidateEvent candidateEvent,
            final RenderingOptions renderingOptions, final OutputStream resultStream) {
        final CandidateSession candidateSession = candidateEvent.getCandidateSession();
        final Delivery delivery = candidateSession.getDelivery();
        final DeliverySettings deliverySettings = delivery.getDeliverySettings();
        final AssessmentPackage assessmentPackage = entityGraphService.getCurrentAssessmentPackage(delivery);

        final TerminatedRenderingRequest renderingRequest = new TerminatedRenderingRequest();
        initBaseRenderingRequest(renderingRequest, assessmentPackage, deliverySettings, renderingOptions);

        assessmentRenderer.renderTeminated(renderingRequest, resultStream);
    }

    private void doRendering(final CandidateEvent candidateEvent, final StandaloneItemRenderingRequest renderingRequest, final OutputStream resultStream) {
        candidateAuditLogger.logStandaloneItemRendering(candidateEvent, renderingRequest);
        final List<CandidateEventNotification> notifications = candidateEvent.getNotifications();
        assessmentRenderer.renderStandaloneItem(renderingRequest, notifications, resultStream);
    }

    private StandaloneItemRenderingRequest initItemRenderingRequest(final CandidateEvent candidateEvent,
            final ItemSessionState itemSessionState, final RenderingOptions renderingOptions,
            final RenderingMode renderingMode) {
        final CandidateSession candidateSession = candidateEvent.getCandidateSession();
        final Delivery delivery = candidateSession.getDelivery();
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) delivery.getDeliverySettings();
        final AssessmentPackage assessmentPackage = entityGraphService.getCurrentAssessmentPackage(delivery);

        final StandaloneItemRenderingRequest renderingRequest = new StandaloneItemRenderingRequest();
        initBaseRenderingRequest(renderingRequest, assessmentPackage, itemDeliverySettings, renderingOptions);
        renderingRequest.setAssessmentItemUri(renderingRequest.getAssessmentResourceUri()); /* (These are the same for standalone items) */
        renderingRequest.setRenderingMode(renderingMode);
        renderingRequest.setItemSessionState(itemSessionState);
        renderingRequest.setPrompt(itemDeliverySettings.getPrompt());
        return renderingRequest;
    }

    private void initBaseRenderingRequest(final AbstractRenderingRequest renderingRequest,
            final AssessmentPackage assessmentPackage, final DeliverySettings deliverySettings,
            final RenderingOptions renderingOptions) {
        renderingRequest.setAssessmentResourceLocator(assessmentPackageFileService.createResolvingResourceLocator(assessmentPackage));
        renderingRequest.setAssessmentResourceUri(assessmentPackageFileService.createAssessmentObjectUri(assessmentPackage));
        renderingRequest.setAuthorMode(deliverySettings.isAuthorMode());
        renderingRequest.setRenderingOptions(renderingOptions);
    }

    /**
     * Computes the current value for the <code>duration</code> variable for this session.
     * <p>
     * Currently, this is just the length of time since the session was first opened.
     * We DO NOT yet support breaking sessions time-wise.
     *
     * @return computed value for <code>duration</code>, which will be non-negative.
     */
    private double computeItemSessionDuration(final CandidateSession candidateSession) {
        final long startTime = candidateSession.getCreationTime().getTime();
        final long currentTime = requestTimestampContext.getCurrentRequestTimestamp().getTime();

        final double duration = (currentTime - startTime) / 1000.0;
        return duration;
    }

    //----------------------------------------------------
    // Attempt

    public void handleAttempt(final long xid, final String sessionToken,
            final Map<Identifier, StringResponseData> stringResponseMap,
            final Map<Identifier, MultipartFile> fileResponseMap)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        handleAttempt(candidateSession, stringResponseMap, fileResponseMap);
    }

    public void handleAttempt(final CandidateSession candidateSession,
            final Map<Identifier, StringResponseData> stringResponseMap,
            final Map<Identifier, MultipartFile> fileResponseMap)
            throws CandidateForbiddenException {
        Assert.notNull(candidateSession, "candidateSession");

        /* Set up listener to record any notifications from JQTI candidateAuditLogger.logic */
        final NotificationRecorder notificationRecorder = new NotificationRecorder(NotificationLevel.INFO);

        /* Get current JQTI state and create JQTI controller */
        final CandidateEvent mostRecentEvent = candidateDataServices.getMostRecentItemEvent(candidateSession);
        final ItemSessionController itemSessionController = candidateDataServices.createItemSessionController(mostRecentEvent, notificationRecorder);

        /* Make sure an attempt is allowed */
        final ItemSessionState itemSessionState = itemSessionController.getItemSessionState();
        if (itemSessionState.isClosed()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.MAKE_ATTEMPT);
        }

        /* Build response map in required format for JQTI+.
         * NB: The following doesn't test for duplicate keys in the two maps. I'm not sure
         * it's worth the effort.
         */
        final Map<Identifier, ResponseData> responseDataMap = new HashMap<Identifier, ResponseData>();
        if (stringResponseMap!=null) {
            for (final Entry<Identifier, StringResponseData> stringResponseEntry : stringResponseMap.entrySet()) {
                final Identifier identifier = stringResponseEntry.getKey();
                final StringResponseData stringResponseData = stringResponseEntry.getValue();
                responseDataMap.put(identifier, stringResponseData);
            }
        }
        final Map<Identifier, CandidateFileSubmission> fileSubmissionMap = new HashMap<Identifier, CandidateFileSubmission>();
        if (fileResponseMap!=null) {
            for (final Entry<Identifier, MultipartFile> fileResponseEntry : fileResponseMap.entrySet()) {
                final Identifier identifier = fileResponseEntry.getKey();
                final MultipartFile multipartFile = fileResponseEntry.getValue();
                final CandidateFileSubmission fileSubmission = candidateUploadService.importFileSubmission(candidateSession, multipartFile);
                final FileResponseData fileResponseData = new FileResponseData(new File(fileSubmission.getStoredFilePath()), fileSubmission.getContentType());
                responseDataMap.put(identifier, fileResponseData);
                fileSubmissionMap.put(identifier, fileSubmission);
            }
        }

        /* Build Map of responses in appropriate entity form.
         * NB: Not ready for persisting yet. */
        final Map<Identifier, CandidateResponse> candidateResponseMap = new HashMap<Identifier, CandidateResponse>();
        for (final Entry<Identifier, ResponseData> responseEntry : responseDataMap.entrySet()) {
            final Identifier responseIdentifier = responseEntry.getKey();
            final ResponseData responseData = responseEntry.getValue();

            final CandidateResponse candidateResponse = new CandidateResponse();
            candidateResponse.setResponseIdentifier(responseIdentifier.toString());
            candidateResponse.setResponseDataType(responseData.getType());
            candidateResponse.setResponseLegality(ResponseLegality.VALID); /* (May change this below) */
            switch (responseData.getType()) {
                case STRING:
                    candidateResponse.setStringResponseData(((StringResponseData) responseData).getResponseData());
                    break;

                case FILE:
                    candidateResponse.setFileSubmission(fileSubmissionMap.get(responseIdentifier));
                    break;

                default:
                    throw new QtiWorksLogicException("Unexpected switch case: " + responseData.getType());
            }
            candidateResponseMap.put(responseIdentifier, candidateResponse);
        }

        /* Attempt to bind responses */
        itemSessionController.bindResponses(responseDataMap);
        itemSessionController.markPendingResponseProcessing();

        /* Note any responses that failed to bind */
        final Set<Identifier> badResponseIdentifiers = itemSessionState.getUnboundResponseIdentifiers();
        final boolean allResponsesBound = badResponseIdentifiers.isEmpty();
        for (final Identifier badResponseIdentifier : badResponseIdentifiers) {
            candidateResponseMap.get(badResponseIdentifier).setResponseLegality(ResponseLegality.BAD);
        }

        /* Now validate the responses according to any constraints specified by the interactions */
        boolean allResponsesValid = false;
        if (allResponsesBound) {
            final Set<Identifier> invalidResponseIdentifiers = itemSessionState.getInvalidResponseIdentifiers();
            allResponsesValid = invalidResponseIdentifiers.isEmpty();
            if (!allResponsesValid) {
                /* Some responses not valid, so note these down */
                for (final Identifier invalidResponseIdentifier : invalidResponseIdentifiers) {
                    candidateResponseMap.get(invalidResponseIdentifier).setResponseLegality(ResponseLegality.INVALID);
                }
            }
        }

        /* Invoke response processing (only if responses are valid) */
        if (allResponsesValid) {
            itemSessionController.performResponseProcessing();
        }

        /* Update JQTI state */
        itemSessionState.setDuration(computeItemSessionDuration(candidateSession));

        /* Record resulting attempt and event */
        final CandidateItemEventType eventType = allResponsesBound ?
            (allResponsesValid ? CandidateItemEventType.ATTEMPT_VALID : CandidateItemEventType.ATTEMPT_INVALID)
            : CandidateItemEventType.ATTEMPT_BAD;
        final CandidateEvent candidateEvent = candidateDataServices.recordCandidateItemEvent(candidateSession,
                eventType, itemSessionState, notificationRecorder);
        candidateAuditLogger.logCandidateEvent(candidateSession, candidateEvent);

        /* Link and persist CandidateResponse entities */
        for (final CandidateResponse candidateResponse : candidateResponseMap.values()) {
            candidateResponse.setCandidateEvent(candidateEvent);
            candidateResponseDao.persist(candidateResponse);
        }

        /* Check whether processing wants to close the session and persist state */
        if (itemSessionState.isClosed()) {
            candidateDataServices.computeAndRecordItemAssessmentResult(candidateSession, itemSessionController);
            candidateSession.setClosed(true);
        }
        candidateSessionDao.update(candidateSession);
    }

    //----------------------------------------------------
    // Session close(by candidate)

    /**
     * Closes the {@link CandidateSession} having the given ID (xid), moving it
     * into closed state.
     */
    public CandidateSession closeCandidateSession(final long xid, final String sessionToken)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        return closeCandidateSession(candidateSession);
    }

    public CandidateSession closeCandidateSession(final CandidateSession candidateSession)
            throws CandidateForbiddenException {
        Assert.notNull(candidateSession, "candidateSession");

        /* Get current session state */
        final ItemSessionState itemSessionState = candidateDataServices.computeCurrentItemSessionState(candidateSession);

        /* Check this is allowed in current state */
        ensureSessionNotTerminated(candidateSession);
        final Delivery delivery = candidateSession.getDelivery();
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) delivery.getDeliverySettings();
        if (itemSessionState.isClosed()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.CLOSE_SESSION_WHEN_CLOSED);
        }
        else if (!itemDeliverySettings.isAllowClose()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.CLOSE_SESSION_WHEN_INTERACTING);
        }

        /* Update state */
        final NotificationRecorder notificationRecorder = new NotificationRecorder(NotificationLevel.INFO);
        final ItemSessionController itemSessionController = candidateDataServices.createItemSessionController(delivery,
                itemSessionState, notificationRecorder);
        itemSessionController.markClosed();
        itemSessionState.setDuration(computeItemSessionDuration(candidateSession));

        /* Record and log event */
        final CandidateEvent candidateEvent = candidateDataServices.recordCandidateItemEvent(candidateSession,
                CandidateItemEventType.CLOSE, itemSessionState, notificationRecorder);
        candidateAuditLogger.logCandidateEvent(candidateSession, candidateEvent);

        /* Update session state and record result */
        candidateDataServices.computeAndRecordItemAssessmentResult(candidateSession, itemSessionController);
        candidateSession.setClosed(true);
        candidateSessionDao.update(candidateSession);

        return candidateSession;
    }

    //----------------------------------------------------
    // Session reinit

    /**
     * Re-initialises the {@link CandidateSession} having the given ID (xid), returning the
     * updated {@link CandidateSession}. At QTI level, this reruns template processing, so
     * randomised values will change as a result of this process.
     */
    public CandidateSession reinitCandidateSession(final long xid, final String sessionToken)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        return reinitCandidateSession(candidateSession);
    }

    public CandidateSession reinitCandidateSession(final CandidateSession candidateSession)
            throws CandidateForbiddenException {
        Assert.notNull(candidateSession, "candidateSession");

        /* Get current session state */
        ItemSessionState itemSessionState = candidateDataServices.computeCurrentItemSessionState(candidateSession);

        /* Make sure caller may reinit the session */
        ensureSessionNotTerminated(candidateSession);
        final Delivery delivery = candidateSession.getDelivery();
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) delivery.getDeliverySettings();
        if (!itemSessionState.isClosed() && !itemDeliverySettings.isAllowReinitWhenInteracting()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.REINIT_SESSION_WHEN_INTERACTING);
        }
        else if (itemSessionState.isClosed() && !itemDeliverySettings.isAllowReinitWhenClosed()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.REINIT_SESSION_WHEN_CLOSED);
        }

        /* Might as well just create fresh JQTI+ state */
        itemSessionState = new ItemSessionState();

        /* Update session state */
        final NotificationRecorder notificationRecorder = new NotificationRecorder(NotificationLevel.INFO);
        final ItemSessionController itemSessionController = candidateDataServices.createItemSessionController(delivery,
                itemSessionState, notificationRecorder);
        itemSessionController.initialize();
        itemSessionController.performTemplateProcessing();
        itemSessionState.setDuration(computeItemSessionDuration(candidateSession));

        /* Mark item as being presented */
        itemSessionController.markPresented();

        /* Maybe mark as pending submission */
        if (!itemSessionState.isClosed()) {
            itemSessionController.markPendingSubmission();
        }

        /* Record and log event */
        itemSessionState.setDuration(computeItemSessionDuration(candidateSession));
        final CandidateEvent candidateEvent = candidateDataServices.recordCandidateItemEvent(candidateSession,
                CandidateItemEventType.REINIT, itemSessionState, notificationRecorder);
        candidateAuditLogger.logCandidateEvent(candidateSession, candidateEvent);

        /* Update session depending on state after processing. Record final result if session closed immediately */
        candidateSession.setClosed(itemSessionState.isClosed());
        if (itemSessionState.isClosed()) {
            candidateDataServices.computeAndRecordItemAssessmentResult(candidateSession, itemSessionController);
        }
        candidateSessionDao.update(candidateSession);

        return candidateSession;
    }

    //----------------------------------------------------
    // Session reset

    /**
     * Resets the {@link CandidateSession} having the given ID (xid), returning the
     * updated {@link CandidateSession}. This takes the session back to the state it
     * was in immediately after the last {@link CandidateItemEventType#REINIT} (if applicable),
     * or after the original {@link CandidateEvent#INIT}.
     */
    public CandidateSession resetCandidateSession(final long xid, final String sessionToken)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        return resetCandidateSession(candidateSession);
    }

    public CandidateSession resetCandidateSession(final CandidateSession candidateSession)
            throws CandidateForbiddenException {
        Assert.notNull(candidateSession, "candidateSession");

        /* Get current session state */
        final ItemSessionState itemSessionState = candidateDataServices.computeCurrentItemSessionState(candidateSession);

        /* Make sure caller may reset the session */
        ensureSessionNotTerminated(candidateSession);
        final Delivery delivery = candidateSession.getDelivery();
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) delivery.getDeliverySettings();
        if (!itemSessionState.isClosed() && !itemDeliverySettings.isAllowResetWhenInteracting()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.RESET_SESSION_WHEN_INTERACTING);
        }
        else if (!itemSessionState.isClosed() && !itemDeliverySettings.isAllowResetWhenClosed()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.RESET_SESSION_WHEN_CLOSED);
        }

        /* Update state */
        final NotificationRecorder notificationRecorder = new NotificationRecorder(NotificationLevel.INFO);
        final ItemSessionController itemSessionController = candidateDataServices.createItemSessionController(delivery,
                itemSessionState, notificationRecorder);
        itemSessionController.resetItemSession();
        itemSessionState.setDuration(computeItemSessionDuration(candidateSession));

        /* Mark item as being presented */
        itemSessionController.markPresented();

        /* Maybe mark as pending submission */
        if (!itemSessionState.isClosed()) {
            itemSessionController.markPendingSubmission();
        }

        /* Record and event */
        final CandidateEvent candidateEvent = candidateDataServices.recordCandidateItemEvent(candidateSession, CandidateItemEventType.RESET, itemSessionState);
        candidateAuditLogger.logCandidateEvent(candidateSession, candidateEvent);

        /* Update session depending on state after processing. Record final result if session closed immediately */
        candidateSession.setClosed(itemSessionState.isClosed());
        if (itemSessionState.isClosed()) {
            candidateDataServices.computeAndRecordItemAssessmentResult(candidateSession, itemSessionController);
        }
        candidateSessionDao.update(candidateSession);

        return candidateSession;
    }

    //----------------------------------------------------
    // Solution request

    /**
     * Transitions the {@link CandidateSession} having the given ID (xid) into solution state.
     */
    public CandidateSession transitionCandidateSessionToSolutionState(final long xid, final String sessionToken)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        return transitionCandidateSessionToSolutionState(candidateSession);
    }

    public CandidateSession transitionCandidateSessionToSolutionState(final CandidateSession candidateSession)
            throws CandidateForbiddenException {
        Assert.notNull(candidateSession, "candidateSession");

        /* Get current session state */
        final ItemSessionState itemSessionState = candidateDataServices.computeCurrentItemSessionState(candidateSession);

        /* Make sure caller may do this */
        ensureSessionNotTerminated(candidateSession);
        final Delivery delivery = candidateSession.getDelivery();
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) delivery.getDeliverySettings();
        if (!itemSessionState.isClosed() && !itemDeliverySettings.isAllowSolutionWhenInteracting()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.SOLUTION_WHEN_INTERACTING);
        }
        else if (itemSessionState.isClosed() && !itemDeliverySettings.isAllowResetWhenClosed()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.SOLUTION_WHEN_CLOSED);
        }

        /* Update state */
        final NotificationRecorder notificationRecorder = new NotificationRecorder(NotificationLevel.INFO);
        final ItemSessionController itemSessionController = candidateDataServices.createItemSessionController(delivery,
                itemSessionState, notificationRecorder);
        boolean isClosingSession = false;
        if (!itemSessionState.isClosed()) {
            isClosingSession = true;
            itemSessionController.markClosed();
            itemSessionState.setDuration(computeItemSessionDuration(candidateSession));
        }

        /* Record and log event */
        final CandidateEvent candidateEvent = candidateDataServices.recordCandidateItemEvent(candidateSession, CandidateItemEventType.SOLUTION, itemSessionState);
        candidateAuditLogger.logCandidateEvent(candidateSession, candidateEvent);

        /* Update session if required */
        if (isClosingSession) {
            candidateSession.setClosed(true);
            candidateDataServices.computeAndRecordItemAssessmentResult(candidateSession, itemSessionController);
            candidateSessionDao.update(candidateSession);
        }

        return candidateSession;
    }

    //----------------------------------------------------
    // Playback request

    /**
     * Updates the state of the {@link CandidateSession} having the given ID (xid)
     * so that it will play back the {@link CandidateEvent} having the given ID (xeid).
     */
    public CandidateSession setPlaybackState(final long xid, final String sessionToken, final long xeid)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        return setPlaybackState(candidateSession, xeid);
    }

    public CandidateSession setPlaybackState(final CandidateSession candidateSession, final long xeid)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        Assert.notNull(candidateSession, "candidateSession");

        /* Get current session state */
        final ItemSessionState itemSessionState = candidateDataServices.computeCurrentItemSessionState(candidateSession);

        /* Make sure caller may do this */
        ensureSessionNotTerminated(candidateSession);
        final Delivery delivery = candidateSession.getDelivery();
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) delivery.getDeliverySettings();
        if (!itemSessionState.isClosed()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.PLAYBACK_WHEN_INTERACTING);
        }
        else if (itemSessionState.isClosed() && !itemDeliverySettings.isAllowPlayback()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.PLAYBACK);
        }

        /* Look up target event, make sure it belongs to this session and make sure it can be played back */
        final CandidateEvent targetEvent = candidateEventDao.requireFindById(xeid);
        if (targetEvent.getCandidateSession().getId().longValue()!=candidateSession.getId().longValue()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.PLAYBACK_OTHER_SESSION);
        }
        if (targetEvent.getCategoryEventCategory()!=CandidateEventCategory.ITEM) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.PLAYBACK_EVENT);
        }
        final CandidateItemEventType targetEventType = targetEvent.getItemEventType();
        if (targetEventType==CandidateItemEventType.PLAYBACK || targetEventType==CandidateItemEventType.CLOSE) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.PLAYBACK_EVENT);
        }

        /* Record and event */
        itemSessionState.setDuration(computeItemSessionDuration(candidateSession));
        final CandidateEvent candidateEvent = candidateDataServices.recordCandidateItemEvent(candidateSession,
                CandidateItemEventType.PLAYBACK, itemSessionState, targetEvent);
        candidateAuditLogger.logPlaybackEvent(candidateSession, candidateEvent, targetEvent);

        return candidateSession;
    }

    //----------------------------------------------------
    // Session termination (by candidate)

    /**
     * Terminates the {@link CandidateSession} having the given ID (xid).
     * <p>
     * Currently we're always allowing this action to be made when in
     * interacting or closed states.
     */
    public CandidateSession terminateCandidateSession(final long xid, final String sessionToken)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        return terminateCandidateSession(candidateSession);
    }

    public CandidateSession terminateCandidateSession(final CandidateSession candidateSession)
            throws CandidateForbiddenException {
        Assert.notNull(candidateSession, "candidateSession");

        /* Check session has not already been terminated */
        final Delivery delivery = candidateSession.getDelivery();
        ensureSessionNotTerminated(candidateSession);

        /* Get current session state */
        final ItemSessionState itemSessionState = candidateDataServices.computeCurrentItemSessionState(candidateSession);


        /* Record and log event */
        itemSessionState.setDuration(computeItemSessionDuration(candidateSession));
        final CandidateEvent candidateEvent = candidateDataServices.recordCandidateItemEvent(candidateSession,
                CandidateItemEventType.TERMINATE, itemSessionState);
        candidateAuditLogger.logCandidateEvent(candidateSession, candidateEvent);

        /* Are we terminating a session that hasn't been closed? If so, record the final result. */
        if (!itemSessionState.isClosed()) {
            final NotificationRecorder notificationRecorder = new NotificationRecorder(NotificationLevel.INFO);
            final ItemSessionController itemSessionController = candidateDataServices.createItemSessionController(delivery,
                    itemSessionState, notificationRecorder);
            candidateDataServices.computeAndRecordItemAssessmentResult(candidateSession, itemSessionController);
        }

        /* Update session */
        candidateSession.setTerminated(true);
        candidateSessionDao.update(candidateSession);

        return candidateSession;
    }

    //----------------------------------------------------
    // Access to additional package resources (e.g. images/CSS)

    public void streamAssessmentFile(final CandidateSession candidateSession, final String fileSystemIdString,
            final OutputStreamer outputStreamer)
            throws CandidateForbiddenException, IOException {
        Assert.notNull(candidateSession, "candidateSession");
        Assert.notNull(fileSystemIdString, "fileSystemIdString");
        Assert.notNull(outputStreamer, "outputStreamer");

        /* Make sure requested file is whitelisted for access */
        final Delivery delivery = candidateSession.getDelivery();
        final AssessmentPackage assessmentPackage = entityGraphService.getCurrentAssessmentPackage(delivery);
        String resultingFileHref = null;
        for (final String safeFileHref : assessmentPackage.getSafeFileHrefs()) {
            final URI fileUri = assessmentPackageFileService.createAssessmentFileUri(assessmentPackage, safeFileHref);
            if (fileUri.toString().equals(fileSystemIdString)) {
                resultingFileHref = safeFileHref;
                break;
            }
        }
        if (resultingFileHref==null) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.ACCESS_BLACKLISTED_ASSESSMENT_FILE);
        }

        /* Finally stream the required resource */
        assessmentPackageFileService.streamAssessmentPackageFile(assessmentPackage, resultingFileHref, outputStreamer);
    }

    //----------------------------------------------------
    // Candidate Source access

    public void streamAssessmentSource(final long xid, final String sessionToken, final OutputStreamer outputStreamer)
            throws CandidateForbiddenException, IOException, DomainEntityNotFoundException {
        Assert.notNull(outputStreamer, "outputStreamer");
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        streamAssessmentSource(candidateSession, outputStreamer);
    }

    public void streamAssessmentSource(final CandidateSession candidateSession, final OutputStreamer outputStreamer)
            throws CandidateForbiddenException, IOException {
        Assert.notNull(candidateSession, "candidateSession");
        Assert.notNull(outputStreamer, "outputStreamer");
        ensureCallerMayViewSource(candidateSession);
        final Delivery itemDelivery = candidateSession.getDelivery();
        final AssessmentPackage assessmentPackage = entityGraphService.getCurrentAssessmentPackage(itemDelivery);

        assessmentPackageFileService.streamAssessmentPackageSource(assessmentPackage, outputStreamer);
        candidateAuditLogger.logAction(candidateSession, "ACCESS_SOURCE");
    }

    private void ensureCallerMayViewSource(final CandidateSession candidateSession)
            throws CandidateForbiddenException {
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) candidateSession.getDelivery().getDeliverySettings();
        if (!itemDeliverySettings.isAllowSource()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.VIEW_ASSESSMENT_SOURCE);
        }
    }

    //----------------------------------------------------
    // Candidate Result access

    public void streamItemResult(final long xid, final String sessionToken, final OutputStream outputStream)
            throws CandidateForbiddenException, DomainEntityNotFoundException {
        Assert.notNull(outputStream, "outputStream");
        final CandidateSession candidateSession = lookupCandidateSession(xid, sessionToken);
        streamAssessmentResult(candidateSession, outputStream);
    }

    public void streamAssessmentResult(final CandidateSession candidateSession, final OutputStream outputStream)
            throws CandidateForbiddenException {
        Assert.notNull(candidateSession, "candidateSession");
        Assert.notNull(outputStream, "outputStream");

        /* Forbid results if the candidate session is closed */
        ensureSessionNotTerminated(candidateSession);

        /* Make sure candidate is actually allowed to get results for this delivery */
        ensureCallerMayViewResult(candidateSession);

        /* Get current state */
        final CandidateEvent mostRecentEvent = candidateDataServices.getMostRecentItemEvent(candidateSession);

        /* Generate result Object from state */
        final ItemSessionController itemSessionController = candidateDataServices.createItemSessionController(mostRecentEvent, null);
        final AssessmentResult assessmentResult = candidateDataServices.computeItemAssessmentResult(candidateSession, itemSessionController);

        /* Send result */
        qtiSerializer.serializeJqtiObject(assessmentResult, outputStream);
        candidateAuditLogger.logAction(candidateSession, "ACCESS_RESULT");
    }

    private void ensureCallerMayViewResult(final CandidateSession candidateSession)
            throws CandidateForbiddenException {
        final ItemDeliverySettings itemDeliverySettings = (ItemDeliverySettings) candidateSession.getDelivery().getDeliverySettings();
        if (!itemDeliverySettings.isAllowResult()) {
            candidateAuditLogger.logAndForbid(candidateSession, CandidatePrivilege.VIEW_ASSESSMENT_RESULT);
        }
    }

    //----------------------------------------------------
    // Utilities

    /**
     * Returns a List of IDs (xeid) of all {@link CandidateEvent}s in the given
     * {@link CandidateSession} that a candidate may play back.
     *
     * @param candidateSession
     * @return
     */
    private List<CandidateEvent> getPlaybackEvents(final CandidateSession candidateSession) {
        final List<CandidateEvent> events = candidateEventDao.getForSession(candidateSession, CandidateEventCategory.ITEM);
        final List<CandidateEvent> result = new ArrayList<CandidateEvent>(events.size());
        for (final CandidateEvent event : events) {
            if (isCandidatePlaybackCapable(event)) {
                result.add(event);
            }
        }
        return result;
    }

    private boolean isCandidatePlaybackCapable(final CandidateEvent event) {
        final CandidateItemEventType eventType = event.getItemEventType();
        return eventType==CandidateItemEventType.ATTEMPT_VALID
                || eventType==CandidateItemEventType.ATTEMPT_INVALID
                || eventType==CandidateItemEventType.ATTEMPT_BAD
                || eventType==CandidateItemEventType.INIT
                || eventType==CandidateItemEventType.REINIT
                || eventType==CandidateItemEventType.RESET;
    }
}
