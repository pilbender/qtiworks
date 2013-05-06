/* Copyright (c) 2012-2013, University of Edinburgh.
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
package uk.ac.ed.ph.qtiworks.rendering;

import uk.ac.ed.ph.qtiworks.config.beans.QtiWorksProperties;
import uk.ac.ed.ph.qtiworks.domain.entities.CandidateEventNotification;
import uk.ac.ed.ph.qtiworks.utils.XmlUtilities;

import uk.ac.ed.ph.jqtiplus.JqtiExtensionManager;
import uk.ac.ed.ph.jqtiplus.internal.util.Assert;
import uk.ac.ed.ph.jqtiplus.node.test.NavigationMode;
import uk.ac.ed.ph.jqtiplus.node.test.TestPart;
import uk.ac.ed.ph.jqtiplus.running.TestSessionController;
import uk.ac.ed.ph.jqtiplus.state.EffectiveItemSessionControl;
import uk.ac.ed.ph.jqtiplus.state.ItemSessionState;
import uk.ac.ed.ph.jqtiplus.state.TestPartSessionState;
import uk.ac.ed.ph.jqtiplus.state.TestPlanNode;
import uk.ac.ed.ph.jqtiplus.state.TestPlanNodeKey;
import uk.ac.ed.ph.jqtiplus.state.TestSessionState;
import uk.ac.ed.ph.jqtiplus.state.marshalling.ItemSessionStateXmlMarshaller;
import uk.ac.ed.ph.jqtiplus.state.marshalling.TestSessionStateXmlMarshaller;
import uk.ac.ed.ph.jqtiplus.xmlutils.locators.ClassPathResourceLocator;
import uk.ac.ed.ph.jqtiplus.xmlutils.locators.ResourceLocator;
import uk.ac.ed.ph.jqtiplus.xmlutils.xslt.XsltStylesheetCache;
import uk.ac.ed.ph.jqtiplus.xmlutils.xslt.XsltStylesheetManager;

import uk.ac.ed.ph.snuggletex.XMLStringOutputOptions;
import uk.ac.ed.ph.snuggletex.internal.util.XMLUtilities;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Validator;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * This key service performs the actual rendering of items and tests, supporting the
 * rendering of specific states and particular modal states (e.g. reviewing an item
 * in a test during testPart review).
 * <p>
 * This stands separately from the main QTIWorks domain model and is potentially usable
 * outside of the rest of the QTIWorks engine. I've chosen not to make it a separate module
 * for now.
 *
 * <h2>Usage</h2>
 * <ul>
 *   <li>An instance of this class is safe to use concurrently by multiple threads.</li>
 *   <li>If using outside QTIWorks engine, remember to set the necessary properties then call {@link #init()}</li>
 * </ul>
 *
 * @author David McKain
 */
@Service
public class AssessmentRenderer {

    private static final Logger logger = LoggerFactory.getLogger(AssessmentRenderer.class);

    private static final URI serializeXsltUri = URI.create("classpath:/rendering-xslt/serialize.xsl");
    private static final URI ctopXsltUri = URI.create("classpath:/rendering-xslt/ctop.xsl");
    private static final URI terminatedXsltUri = URI.create("classpath:/rendering-xslt/terminated.xsl");
    private static final URI itemStandaloneXsltUri = URI.create("classpath:/rendering-xslt/item-standalone.xsl");
    private static final URI itemAuthorDebugXsltUri = URI.create("classpath:/rendering-xslt/item-author-debug.xsl");
    private static final URI testItemXsltUri = URI.create("classpath:/rendering-xslt/test-item.xsl");
    private static final URI testEntryXsltUri = URI.create("classpath:/rendering-xslt/test-entry.xsl");
    private static final URI testPartNavigationXsltUri = URI.create("classpath:/rendering-xslt/test-testpart-navigation.xsl");
    private static final URI testPartFeedbackXsltUri = URI.create("classpath:/rendering-xslt/test-testpart-feedback.xsl");
    private static final URI testFeedbackXsltUri = URI.create("classpath:/rendering-xslt/test-feedback.xsl");

    @Resource
    private QtiWorksProperties qtiWorksProperties;

    @Resource
    private JqtiExtensionManager jqtiExtensionManager;

    @Resource
    private XsltStylesheetCache xsltStylesheetCache;

    @Resource
    private Validator jsr303Validator;

    @Resource
    private String webappContextPath;

    /** Manager for the XSLT stylesheets, created during init. */
    private XsltStylesheetManager stylesheetManager;

    //----------------------------------------------------

    public QtiWorksProperties getQtiWorksProperties() {
        return qtiWorksProperties;
    }

    public void setQtiWorksProperties(final QtiWorksProperties qtiWorksProperties) {
        this.qtiWorksProperties = qtiWorksProperties;
    }


    public JqtiExtensionManager getJqtiExtensionManager() {
        return jqtiExtensionManager;
    }

    public void setJqtiExtensionManager(final JqtiExtensionManager jqtiExtensionManager) {
        this.jqtiExtensionManager = jqtiExtensionManager;
    }


    public XsltStylesheetCache getXsltStylesheetCache() {
        return xsltStylesheetCache;
    }

    public void setXsltStylesheetCache(final XsltStylesheetCache xsltStylesheetCache) {
        this.xsltStylesheetCache = xsltStylesheetCache;
    }


    public Validator getJsr303Validator() {
        return jsr303Validator;
    }

    public void setJsr303Validator(final Validator jsr303Validator) {
        this.jsr303Validator = jsr303Validator;
    }


    public String getWebappContextPath() {
        return webappContextPath;
    }

    public void setWebappContextPath(final String webappContextPath) {
        this.webappContextPath = webappContextPath;
    }

    //----------------------------------------------------

    @PostConstruct
    public void init() {
        this.stylesheetManager = new XsltStylesheetManager(new ClassPathResourceLocator(), xsltStylesheetCache);
    }

    //----------------------------------------------------

    /**
     * Renders a terminated session, sending the result to the provided JAXP {@link Result}.
     * <p>
     * NB: If you're using a {@link StreamResult} then you probably want to wrap it around an
     * {@link OutputStream} rather than a {@link Writer}. Remember that you are responsible for
     * closing the {@link OutputStream} or {@link Writer} afterwards!
     */
    public void renderTeminated(final AbstractRenderingRequest<?> request, final Result result) {
        Assert.notNull(result, "result");

        final Map<String, Object> xsltParameters = new HashMap<String, Object>();
        setBaseRenderingParameters(xsltParameters);

        doTransform(request, terminatedXsltUri, xsltParameters, result);
    }

    /**
     * Renders the given {@link ItemRenderingRequest}, sending the result to the provided JAXP {@link Result}.
     * <p>
     * The rendering shows the current state of the item, unless {@link ItemRenderingRequest#isSolutionMode()}
     * returns true, in which case the model solution is rendered.
     * <p>
     * NB: If you're using a {@link StreamResult} then you probably want to wrap it around an
     * {@link OutputStream} rather than a {@link Writer}. Remember that you are responsible for
     * closing the {@link OutputStream} or {@link Writer} afterwards!
     * The caller is responsible for closing this stream afterwards.
     */
    public void renderItem(final ItemRenderingRequest request,
            final List<CandidateEventNotification> notifications, final Result result) {
        Assert.notNull(request, "request");
        Assert.notNull(result, "result");

        /* Check request is valid */
        final BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "itemRenderingRequest");
        jsr303Validator.validate(request, errors);
        if (errors.hasErrors()) {
            throw new IllegalArgumentException("Invalid " + request.getClass().getSimpleName()
                    + " Object: " + errors);
        }

        /* Pass request info to XSLT as parameters */
        final Map<String, Object> xsltParameters = new HashMap<String, Object>();
        setBaseRenderingParameters(xsltParameters, request, notifications);

        /* Pass ItemSessionState (as DOM Document) */
        final ItemSessionState itemSessionState = request.getItemSessionState();
        xsltParameters.put("itemSessionState", ItemSessionStateXmlMarshaller.marshal(itemSessionState).getDocumentElement());

        /* Set control parameters */
        xsltParameters.put("prompt", request.getPrompt());
        xsltParameters.put("solutionMode", Boolean.valueOf(request.isSolutionMode()));
        xsltParameters.put("endAllowed", Boolean.valueOf(request.isEndAllowed()));
        xsltParameters.put("softSoftResetAllowed", Boolean.valueOf(request.isSoftResetAllowed()));
        xsltParameters.put("hardResetAllowed", Boolean.valueOf(request.isHardResetAllowed()));
        xsltParameters.put("solutionAllowed", Boolean.valueOf(request.isSolutionAllowed()));
        xsltParameters.put("candidateCommentAllowed", Boolean.valueOf(request.isCandidateCommentAllowed()));

        /* Set action URLs */
        final ItemRenderingOptions renderingOptions = request.getRenderingOptions();
        xsltParameters.put("endUrl", renderingOptions.getEndUrl());
        xsltParameters.put("softResetUrl", renderingOptions.getSoftResetUrl());
        xsltParameters.put("hardResetUrl", renderingOptions.getHardResetUrl());
        xsltParameters.put("exitUrl", renderingOptions.getExitUrl());
        xsltParameters.put("solutionUrl", renderingOptions.getSolutionUrl());

        /* Perform transform */
        doTransform(request, itemStandaloneXsltUri, xsltParameters, result);
    }

    /**
     * Renders the {@link ItemRenderingRequest}, sending the result to the provided JAXP {@link Result}.
     * <p>
     * The rendering shows the current state of the item, unless {@link ItemRenderingRequest#isSolutionMode()}
     * returns true, in which case the model solution is rendered.
     * <p>
     * NB: If you're using a {@link StreamResult} then you probably want to wrap it around an
     * {@link OutputStream} rather than a {@link Writer}. Remember that you are responsible for
     * closing the {@link OutputStream} or {@link Writer} afterwards!
     * The caller is responsible for closing this stream afterwards.
     */
    public void renderItemAuthorView(final ItemAuthorViewRenderingRequest request,
            final List<CandidateEventNotification> notifications, final Result result) {
        Assert.notNull(request, "request");
        Assert.notNull(result, "result");

        /* Check request is valid */
        final BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "itemAuthorViewRenderingRequest");
        jsr303Validator.validate(request, errors);
        if (errors.hasErrors()) {
            throw new IllegalArgumentException("Invalid " + request.getClass().getSimpleName()
                    + " Object: " + errors);
        }

        /* Pass request info to XSLT as parameters */
        final Map<String, Object> xsltParameters = new HashMap<String, Object>();
        setBaseRenderingParameters(xsltParameters, request, notifications);

        /* Pass ItemSessionState (as DOM Document and XML text) */
        final ItemSessionState itemSessionState = request.getItemSessionState();
        final Document itemSessionStateDocument = ItemSessionStateXmlMarshaller.marshal(itemSessionState);
        xsltParameters.put("itemSessionState", itemSessionStateDocument.getDocumentElement());
        xsltParameters.put("itemSessionStateXml",  serializeDocument(itemSessionStateDocument));

        /* Set control parameters */
        xsltParameters.put("solutionMode", Boolean.valueOf(request.isSolutionMode()));

        /* Perform transform */
        doTransform(request, itemAuthorDebugXsltUri, xsltParameters, result);
    }

    private static String serializeDocument(final Document document) {
        final XMLStringOutputOptions outputOptions = new XMLStringOutputOptions();
        outputOptions.setIndenting(true);
        return XMLUtilities.serializeNode(document, outputOptions);
    }

    /**
     * Renders the given {@link TestItemRenderingDetails}, sending the result to the provided JAXP {@link Result}.
     * <p>
     * NB: If you're using a {@link StreamResult} then you probably want to wrap it around an
     * {@link OutputStream} rather than a {@link Writer}. Remember that you are responsible for
     * closing the {@link OutputStream} or {@link Writer} afterwards!
     *
     * @throws QtiWorksRenderingException if an unexpected Exception happens during rendering
     */
    public void renderTest(final TestRenderingRequest request,
            final List<CandidateEventNotification> notifications, final Result result) {
        Assert.notNull(request, "renderingRequest");
        Assert.notNull(result, "result");

        /* Check request is valid */
        final BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "testRenderingRequest");
        jsr303Validator.validate(request, errors);
        if (errors.hasErrors()) {
            throw new IllegalArgumentException("Invalid " + request.getClass().getSimpleName()
                    + " Object: " + errors);
        }

        /* Set up general XSLT parameters */
        final Map<String, Object> xsltParameters = new HashMap<String, Object>();
        setBaseRenderingParameters(xsltParameters, request, notifications);

        final TestSessionController testSessionController = request.getTestSessionController();
        final TestSessionState testSessionState = testSessionController.getTestSessionState();
        xsltParameters.put("testSessionState", TestSessionStateXmlMarshaller.marshal(testSessionState).getDocumentElement());
        xsltParameters.put("testSystemId", request.getAssessmentResourceUri().toString());

        /* Pass rendering options */
        final TestRenderingOptions renderingOptions = request.getRenderingOptions();
        xsltParameters.put("testPartNavigationUrl", renderingOptions.getTestPartNavigationUrl());
        xsltParameters.put("selectTestItemUrl", renderingOptions.getSelectTestItemUrl());
        xsltParameters.put("advanceTestItemUrl", renderingOptions.getAdvanceTestItemUrl());
        xsltParameters.put("endTestPartUrl", renderingOptions.getEndTestPartUrl());
        xsltParameters.put("reviewTestPartUrl", renderingOptions.getReviewTestPartUrl());
        xsltParameters.put("reviewTestItemUrl", renderingOptions.getReviewTestItemUrl());
        xsltParameters.put("showTestItemSolutionUrl", renderingOptions.getShowTestItemSolutionUrl());
        xsltParameters.put("advanceTestPartUrl", renderingOptions.getAdvanceTestPartUrl());
        xsltParameters.put("exitTestUrl", renderingOptions.getExitTestUrl());

        final TestRenderingMode testRenderingMode = request.getTestRenderingMode();
        if (testRenderingMode==TestRenderingMode.ITEM_REVIEW) {
            doRenderTestItemReview(request, xsltParameters, result);
        }
        else if (testRenderingMode==TestRenderingMode.ITEM_SOLUTION) {
            doRenderTestItemSolution(request, xsltParameters, result);
        }
        else {
            /* Render current state */
            final TestPlanNodeKey currentTestPartKey = testSessionState.getCurrentTestPartKey();
            if (testSessionState.isEnded()) {
                /* At end of test, so show overall test feedback */
                doRenderTestFeedback(request, xsltParameters, result);
            }
            else if (currentTestPartKey!=null) {
                final TestPartSessionState currentTestPartSessionState = testSessionState.getTestPartSessionStates().get(currentTestPartKey);
                final TestPlanNodeKey currentItemKey = testSessionState.getCurrentItemKey();
                if (currentItemKey!=null) {
                    /* An item is selected, so render it in appropriate state */
                    doRenderCurrentTestItem(request, xsltParameters, result);
                }
                else {
                    /* No item selected */
                    if (currentTestPartSessionState.isEnded()) {
                        /* testPart has ended, so must be showing testPart feedback */
                        doRenderTestPartFeedback(request, xsltParameters, result);
                    }
                    else {
                        /* testPart not ended, so we must be showing the navigation menu in nonlinear mode */
                        doRenderTestPartNavigation(request, xsltParameters, result);
                    }
                }
            }
            else {
                /* No current testPart == start of multipart test */
                doRenderTestEntry(request, xsltParameters, result);
            }
        }
    }

    private void doRenderTestEntry(final TestRenderingRequest request,
            final Map<String, Object> xsltParameters, final Result result) {
        doTransform(request, testEntryXsltUri, xsltParameters, result);
    }

    private void doRenderTestPartNavigation(final TestRenderingRequest request,
            final Map<String, Object> xsltParameters, final Result result) {
        /* Determine whether candidate may exist testPart */
        final TestSessionController testSessionController = request.getTestSessionController();
        xsltParameters.put("endTestPartAllowed", Boolean.valueOf(testSessionController.mayEndCurrentTestPart()));

        doTransform(request, testPartNavigationXsltUri, xsltParameters, result);
    }

    private void doRenderTestPartFeedback(final TestRenderingRequest request,
            final Map<String, Object> xsltParameters, final Result result) {
        doTransform(request, testPartFeedbackXsltUri, xsltParameters, result);
    }

    private void doRenderTestFeedback(final TestRenderingRequest request,
            final Map<String, Object> xsltParameters, final Result result) {
        doTransform(request, testFeedbackXsltUri, xsltParameters, result);
    }

    private void doRenderCurrentTestItem(final TestRenderingRequest request,
            final Map<String, Object> xsltParameters, final Result result) {
        /* Extract the item to be rendered */
        final TestSessionController testSessionController = request.getTestSessionController();
        final TestSessionState testSessionState = testSessionController.getTestSessionState();
        final TestPlanNodeKey itemKey = testSessionState.getCurrentItemKey();

        /* Set item parameters */
        final URI itemSystemId = setTestItemParameters(request, itemKey, xsltParameters);

        /* Set specific parameters for this rendering */
        final TestPart currentTestPart = testSessionController.getCurrentTestPart();
        final NavigationMode navigationMode = currentTestPart.getNavigationMode();
        xsltParameters.put("reviewMode", Boolean.FALSE);
        xsltParameters.put("solutionMode", Boolean.FALSE);
        xsltParameters.put("advanceTestItemAllowed", Boolean.valueOf(navigationMode==NavigationMode.LINEAR && testSessionController.mayAdvanceItemLinear()));
        xsltParameters.put("testPartNavigationAllowed", Boolean.valueOf(navigationMode==NavigationMode.NONLINEAR));
        xsltParameters.put("endTestPartAllowed", Boolean.valueOf(navigationMode==NavigationMode.LINEAR && testSessionController.mayEndCurrentTestPart()));

        /* We finally do the transform on the _item_ (NB!) */
        doTransform(request, testItemXsltUri, itemSystemId, xsltParameters, result);
    }

    private void doRenderTestItemReview(final TestRenderingRequest request,
            final Map<String, Object> xsltParameters, final Result result) {
        /* Extract item to review */
        final TestPlanNodeKey reviewItemKey = request.getModalItemKey();

        /* Set item parameters */
        final URI itemSystemId = setTestItemParameters(request, reviewItemKey, xsltParameters);

        /* Set specific parameters for this rendering */
        xsltParameters.put("reviewMode", Boolean.TRUE);
        xsltParameters.put("solutionMode", Boolean.FALSE);
        xsltParameters.put("testPartNavigationAllowed", Boolean.FALSE);
        xsltParameters.put("advanceTestItemAllowed", Boolean.FALSE);
        xsltParameters.put("endTestPartAllowed", Boolean.FALSE);

        /* We finally do the transform on the _item_ (NB!) */
        doTransform(request, testItemXsltUri, itemSystemId, xsltParameters, result);
    }

    private void doRenderTestItemSolution(final TestRenderingRequest request,
            final Map<String, Object> xsltParameters, final Result result) {
        /* Extract item to review */
        final TestPlanNodeKey solutionItemKey = request.getModalItemKey();

        /* Set item parameters */
        final URI itemSystemId = setTestItemParameters(request, solutionItemKey, xsltParameters);

        /* Set specific parameters for this rendering */
        xsltParameters.put("reviewMode", Boolean.TRUE);
        xsltParameters.put("solutionMode", Boolean.TRUE);
        xsltParameters.put("testPartNavigationAllowed", Boolean.FALSE);
        xsltParameters.put("advanceTestItemAllowed", Boolean.FALSE);
        xsltParameters.put("endTestPartAllowed", Boolean.FALSE);

        /* We finally do the transform on the _item_ (NB!) */
        doTransform(request, testItemXsltUri, itemSystemId, xsltParameters, result);
    }

    //----------------------------------------------------

    private URI setTestItemParameters(final TestRenderingRequest request, final TestPlanNodeKey itemKey,
            final Map<String, Object> xsltParameters) {
        final TestSessionController testSessionController = request.getTestSessionController();
        final TestSessionState testSessionState = testSessionController.getTestSessionState();
        final TestPlanNode itemRefNode = testSessionState.getTestPlan().getNode(itemKey);
        if (itemRefNode==null) {
            throw new QtiWorksRenderingException("Failed to locate item with key " + itemKey + " in TestPlan");
        }
        final ItemSessionState itemSessionState = testSessionState.getItemSessionStates().get(itemKey);
        if (itemSessionState==null) {
            throw new QtiWorksRenderingException("Failed to locate ItemSessionState for item with key " + itemKey);
        }

        /* Add item-specific parameters */
        xsltParameters.put("itemSessionState", ItemSessionStateXmlMarshaller.marshal(itemSessionState).getDocumentElement());
        xsltParameters.put("itemKey", itemKey.toString());

        /* Pass ItemSessionControl parameters */
        /* (Add any future additional itemSessionControl parameters here as required) */
        final EffectiveItemSessionControl effectiveItemSessionControl = itemRefNode.getEffectiveItemSessionControl();
        xsltParameters.put("allowComment", Boolean.valueOf(effectiveItemSessionControl.isAllowComment()));
        xsltParameters.put("showFeedback", Boolean.valueOf(effectiveItemSessionControl.isShowFeedback()));
        xsltParameters.put("showSolution", Boolean.valueOf(effectiveItemSessionControl.isShowSolution()));

        /* The caller should reset the following parameters to suit */
        xsltParameters.put("reviewMode", Boolean.FALSE);
        xsltParameters.put("solutionMode", Boolean.FALSE);
        xsltParameters.put("testPartNavigationAllowed", Boolean.FALSE);
        xsltParameters.put("advanceTestItemAllowed", Boolean.FALSE);
        xsltParameters.put("endTestPartAllowed", Boolean.FALSE);

        return itemRefNode.getItemSystemId();
    }

    private <P extends AbstractRenderingOptions> void setBaseRenderingParameters(final Map<String, Object> xsltParameters,
            final AbstractRenderingRequest<P> request, final List<CandidateEventNotification> notifications) {
        setBaseRenderingParameters(xsltParameters);

        /* Pass notifications */
        if (notifications!=null) {
            xsltParameters.put("notifications", new XsltParamBuilder().notificationsToElements(notifications));
        }

        /* Pass common control parameters */
        xsltParameters.put("authorMode", request.isAuthorMode());
        xsltParameters.put("sourceAllowed", Boolean.valueOf(request.isSourceAllowed()));
        xsltParameters.put("resultAllowed", Boolean.valueOf(request.isResultAllowed()));

        /* Pass common action URLs */
        final P renderingOptions = request.getRenderingOptions();
        xsltParameters.put("serveFileUrl", renderingOptions.getServeFileUrl());
        xsltParameters.put("responseUrl", renderingOptions.getResponseUrl());
        xsltParameters.put("sourceUrl", renderingOptions.getSourceUrl());
        xsltParameters.put("resultUrl", renderingOptions.getResultUrl());
    }

    private void setBaseRenderingParameters(final Map<String, Object> xsltParameters) {
        xsltParameters.put("qtiWorksVersion", qtiWorksProperties.getQtiWorksVersion());
        xsltParameters.put("webappContextPath", webappContextPath);
    }

    //----------------------------------------------------

    private void doTransform(final AbstractRenderingRequest<?> renderingRequest, final URI rendererStylesheetUri,
            final Map<String, Object> xsltParameters, final Result result) {
        doTransform(renderingRequest, rendererStylesheetUri, renderingRequest.getAssessmentResourceUri(),
                xsltParameters, result);
    }

    private void doTransform(final AbstractRenderingRequest<?> renderingRequest, final URI rendererStylesheetUri,
            final URI inputUri, final Map<String, Object> xsltParameters, final Result result) {
        /* We do this as an XML pipeline:
         *
         * Input --> Rendering XSLT --> MathML C-to-P --> Serialization XSLT --> Result
         *
         * NB: I'm not bothering to set up LexicalHandlers, so comments and things like that won't
         * be passed through the pipeline. If that becomes important, change the code below to
         * support that.
         */
         /* First obtain the required compiled stylesheets. */
        final TransformerHandler rendererTransformerHandler = stylesheetManager.getCompiledStylesheetHandler(rendererStylesheetUri, renderingRequest.getAssessmentResourceLocator());
        final TransformerHandler mathmlTransformerHandler = stylesheetManager.getCompiledStylesheetHandler(ctopXsltUri, null);
        final TransformerHandler serializerTransformerHandler = stylesheetManager.getCompiledStylesheetHandler(serializeXsltUri, null);

        /* Pass necessary parameters to renderer */
        final Transformer rendererTransformer = rendererTransformerHandler.getTransformer();
        rendererTransformer.setParameter("systemId", inputUri);
        if (xsltParameters!=null) {
            for (final Entry<String, Object> paramEntry : xsltParameters.entrySet()) {
                rendererTransformer.setParameter(paramEntry.getKey(), paramEntry.getValue());
            }
        }

        /* Configure the serializer */
        final Transformer serializerTransformer = serializerTransformerHandler.getTransformer();
        final AbstractRenderingOptions renderingOptions = renderingRequest.getRenderingOptions();
        final SerializationMethod serializationMethod = renderingRequest.getRenderingOptions().getSerializationMethod();

        serializerTransformer.setParameter("serializationMethod", serializationMethod.toString());
        serializerTransformer.setParameter("outputMethod", serializationMethod.getMethod());
        serializerTransformer.setParameter("contentType", serializationMethod.getContentType());
        serializerTransformer.setOutputProperty(OutputKeys.INDENT, "yes");
        serializerTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        serializerTransformer.setOutputProperty(OutputKeys.MEDIA_TYPE, serializationMethod.getContentType());
        serializerTransformer.setOutputProperty(OutputKeys.METHOD, serializationMethod.getMethod());
        serializerTransformer.setOutputProperty("include-content-type", "no");

        /* If we're writing to an OutputStream, then select the encoding to use */
        final StreamResult streamResult = (result instanceof StreamResult) ? (StreamResult) result : null;
        final boolean isOutputStreamResult = streamResult!=null && streamResult.getOutputStream()!=null;
        final String outputStreamEncoding = renderingOptions.getEncoding()!=null ? renderingOptions.getEncoding() : "UTF-8";
        if (isOutputStreamResult) {
            serializerTransformer.setOutputProperty(OutputKeys.ENCODING, outputStreamEncoding);
        }

        /* If we're building HTML5, send its custom pseudo-DOCTYPE to the Result, as we can't generate this in XSLT. */
        if (streamResult!=null && serializationMethod==SerializationMethod.HTML5_MATHJAX) {
            final String html5Doctype = "<!DOCTYPE html>\n";
            try {
                if (isOutputStreamResult) {
                    /* Need to send doctype in correct encoding */
                    streamResult.getOutputStream().write(html5Doctype.getBytes(outputStreamEncoding));
                }
                else if (streamResult.getWriter()!=null) {
                    streamResult.getWriter().write(html5Doctype);
                }
            }
            catch (final IOException e) {
                throw new QtiWorksRenderingException("Could not write HTML5 prolog to result", e);
            }
        }

        /* Set up the XML source */
        final ResourceLocator assessmentResourceLocator = renderingRequest.getAssessmentResourceLocator();
        final InputStream assessmentStream = assessmentResourceLocator.findResource(inputUri);
        final InputSource assessmentSaxSource = new InputSource(assessmentStream);
        assessmentSaxSource.setSystemId(inputUri.toString());

        /* Now join the pipeline together (it's clearest to work backwards here)
         *
         * NB: I'm not bothering to set up LexicalHandlers, so comments and things like that won't
         * be passed through the pipeline. If that becomes important, change the code below to
         * support that.
         */
        serializerTransformerHandler.setResult(result);
        final SAXResult mathmlResult = new SAXResult(serializerTransformerHandler);
        mathmlTransformerHandler.setResult(mathmlResult);
        final SAXResult rendererResult = new SAXResult(mathmlTransformerHandler);
        rendererTransformerHandler.setResult(rendererResult);
        final XMLReader xmlReader = XmlUtilities.createNsAwareSaxReader(false);
        xmlReader.setContentHandler(rendererTransformerHandler);

        /* Finally we run the pipeline */
        try {
            xmlReader.parse(assessmentSaxSource);
        }
        catch (final Exception e) {
            logger.error("Rendering XSLT pipeline failed for request {}", renderingRequest, e);
            throw new QtiWorksRenderingException("Unexpected Exception running rendering XML pipeline", e);
        }
    }
}
