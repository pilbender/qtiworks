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
package org.qtitools.qti.node.expression.operator;

import uk.ac.ed.ph.jqtiplus.exception.QtiAttributeException;
import uk.ac.ed.ph.jqtiplus.exception.QtiBaseTypeException;
import uk.ac.ed.ph.jqtiplus.exception.QTICardinalityException;
import uk.ac.ed.ph.jqtiplus.exception.QTIRuntimeException;

import java.util.Arrays;
import java.util.Collection;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.qtitools.qti.node.expression.ExpressionRefuseTest;

/**
 * Test of <code>Equal</code> expression.
 * 
 * @see uk.ac.ed.ph.jqtiplus.node.expression.operator.Equal
 */
@RunWith(Parameterized.class)
public class EqualRefuseTest extends ExpressionRefuseTest {

    /**
     * Creates test data for this test.
     * 
     * @return test data for this test
     */
    @Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                // attributes
                { "<equal>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode=''>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='unknown'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class },
                // attributes - absolute
                { "<equal toleranceMode='absolute'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='absolute' tolerance=''>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='absolute' tolerance='1 2 3'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='absolute' tolerance='1 A'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='absolute' tolerance='-1 1'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='absolute' tolerance='1 -1'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='absolute' tolerance='-1 -1'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='absolute' tolerance='1 1' includeLowerBound='True'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='absolute' tolerance='1 1' includeUpperBound='TRUE'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class },
                // attributes - relative
                { "<equal toleranceMode='relative'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='relative' tolerance=''>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='relative' tolerance='1 2 3'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='relative' tolerance='1 A'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='relative' tolerance='-1 1'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='relative' tolerance='1 -1'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='relative' tolerance='-1 -1'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='relative' tolerance='1 1' includeLowerBound='True'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class }, { "<equal toleranceMode='relative' tolerance='1 1' includeUpperBound='TRUE'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</equal>", QtiAttributeException.class },
                // multiple
                { "<equal toleranceMode='exact'>" +
                        "<multiple>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</multiple>" +
                        "<multiple>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</multiple>" +
                        "</equal>", QTICardinalityException.class },
                // ordered
                { "<equal toleranceMode='exact'>" +
                        "<ordered>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</ordered>" +
                        "<ordered>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</ordered>" +
                        "</equal>", QTICardinalityException.class },
                // record
                { "<equal toleranceMode='exact'>" +
                        "<recordEx identifiers='key_1'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</recordEx>" +
                        "<recordEx identifiers='key_1'>" +
                        "<baseValue baseType='integer'>1</baseValue>" +
                        "</recordEx>" +
                        "</equal>", QTICardinalityException.class },
                // identifier
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='identifier'>identifier</baseValue>" +
                        "<baseValue baseType='identifier'>identifier</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
                // boolean
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='boolean'>true</baseValue>" +
                        "<baseValue baseType='boolean'>true</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
                // string
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='string'>string</baseValue>" +
                        "<baseValue baseType='string'>string</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
                // point
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='point'>1 1</baseValue>" +
                        "<baseValue baseType='point'>1 1</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
                // pair
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='pair'>identifier_1 identifier_2</baseValue>" +
                        "<baseValue baseType='pair'>identifier_1 identifier_2</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
                // directedPair
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='directedPair'>identifier_1 identifier_2</baseValue>" +
                        "<baseValue baseType='directedPair'>identifier_1 identifier_2</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
                // duration
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='duration'>1</baseValue>" +
                        "<baseValue baseType='duration'>1</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
                // file
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='file'>file</baseValue>" +
                        "<baseValue baseType='file'>file</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
                // uri
                { "<equal toleranceMode='exact'>" +
                        "<baseValue baseType='uri'>uri</baseValue>" +
                        "<baseValue baseType='uri'>uri</baseValue>" +
                        "</equal>", QtiBaseTypeException.class },
        });
    }

    /**
     * Constructs <code>Equal</code> expression test.
     * 
     * @param xml xml data used for creation tested expression
     * @param expectedException expected exception during evaluation of tested
     *            expression
     */
    public EqualRefuseTest(String xml, Class<? extends QTIRuntimeException> expectedException) {
        super(xml, expectedException);
    }
}