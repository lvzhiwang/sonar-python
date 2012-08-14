/*
 * Sonar Python Plugin
 * Copyright (C) 2011 SonarSource and Waleri Enns
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.python.xunit;

import java.io.File;

import org.apache.commons.configuration.Configuration;
import org.sonar.api.batch.AbstractCoverageExtension;
import org.sonar.api.batch.DependsUpon;
import org.sonar.api.batch.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Measure;
import org.sonar.api.resources.Qualifiers;
import org.sonar.api.resources.Project;
import org.sonar.api.utils.ParsingUtils;
import org.sonar.api.utils.StaxParser;
import org.sonar.plugins.python.PythonReportSensor;
import org.sonar.plugins.python.Python;
import org.sonar.plugins.python.PythonPlugin;

/**
 * {@inheritDoc}
 */
public class PythonXunitSensor extends PythonReportSensor {
  public static final String REPORT_PATH_KEY = "sonar.python.xunit.reportPath";
  private static final String DEFAULT_REPORT_PATH = "xunit-reports/xunit-result-*.xml";
  private Python lang = null;
  
  /**
   * {@inheritDoc}
   */
  public PythonXunitSensor(Configuration conf, Python lang) {
    super(conf);
    this.lang = lang;
  }
  
  /**
   * {@inheritDoc}
   */
  @DependsUpon
  public Class<?> dependsUponCoverageSensors() {
    return AbstractCoverageExtension.class;
  }

  protected String reportPathKey() {
    return REPORT_PATH_KEY;
  }
  
  protected String defaultReportPath() {
    return DEFAULT_REPORT_PATH;
  }
  
  protected void processReport(final Project project, final SensorContext context, File report)
    throws
    javax.xml.stream.XMLStreamException
  {
    parseReport(project, context, report);
  }
  
  protected void handleNoReportsCase(SensorContext context) {
    context.saveMeasure(CoreMetrics.TESTS, 0.0);
  }
  
  private void parseReport(Project project, SensorContext context, File report)
    throws javax.xml.stream.XMLStreamException
  {
    PythonPlugin.LOG.info("Parsing report '{}'", report);
    
    TestSuiteParser parserHandler = new TestSuiteParser();
    StaxParser parser = new StaxParser(parserHandler, false);
    parser.parse(report);
    
    for (TestSuite fileReport : parserHandler.getParsedReports()) {
      String fileKey = fileReport.getKey();
      
      org.sonar.api.resources.File unitTest =
        org.sonar.api.resources.File.fromIOFile(new File(fileKey), project);
      if (unitTest == null || context.getResource(unitTest) == null) {
        PythonPlugin.LOG.debug("Cannot find the resource for {}, creating a virtual one",
                               fileKey);
        unitTest = createVirtualFile(context, fileKey);
      }
      
      PythonPlugin.LOG.debug("Saving test execution measures for file '{}' under resource '{}'",
                             fileKey, unitTest);
      
      double testsCount = fileReport.getTests() - fileReport.getSkipped();
      context.saveMeasure(unitTest, CoreMetrics.SKIPPED_TESTS, (double)fileReport.getSkipped());
      context.saveMeasure(unitTest, CoreMetrics.TESTS, testsCount);
      context.saveMeasure(unitTest, CoreMetrics.TEST_ERRORS, (double)fileReport.getErrors());
      context.saveMeasure(unitTest, CoreMetrics.TEST_FAILURES, (double)fileReport.getFailures());
      context.saveMeasure(unitTest, CoreMetrics.TEST_EXECUTION_TIME, (double)fileReport.getTime());
      double passedTests = testsCount - fileReport.getErrors() - fileReport.getFailures();
      if (testsCount > 0) {
        double percentage = passedTests * 100d / testsCount;
        context.saveMeasure(unitTest, CoreMetrics.TEST_SUCCESS_DENSITY, ParsingUtils.scaleValue(percentage));
      }
      
      context.saveMeasure(unitTest, new Measure(CoreMetrics.TEST_DATA, fileReport.getDetails()));
    }
  }
  
  private org.sonar.api.resources.File createVirtualFile(SensorContext context,
                                                         String filename) {
    org.sonar.api.resources.File virtualFile =
      new org.sonar.api.resources.File(this.lang, filename);
    virtualFile.setQualifier(Qualifiers.UNIT_TEST_FILE);
    context.saveSource(virtualFile, "<source code could not be found>");
    return virtualFile;
  }
}
