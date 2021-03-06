/*
 * This file is part of dependency-check-core.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2012 Jeremy Long. All Rights Reserved.
 */
package org.owasp.dependencycheck.reporting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.context.Context;
import org.apache.velocity.runtime.RuntimeConstants;
import org.owasp.dependencycheck.analyzer.Analyzer;
import org.owasp.dependencycheck.data.nvdcve.DatabaseProperties;
import org.owasp.dependencycheck.dependency.Dependency;
import org.owasp.dependencycheck.utils.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ReportGenerator is used to, as the name implies, generate reports. Internally the generator uses the Velocity
 * Templating Engine. The ReportGenerator exposes a list of Dependencies to the template when generating the report.
 *
 * @author Jeremy Long
 */
public class ReportGenerator {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ReportGenerator.class);

    /**
     * An enumeration of the report formats.
     */
    public enum Format {

        /**
         * Generate all reports.
         */
        ALL,
        /**
         * Generate XML report.
         */
        XML,
        /**
         * Generate HTML report.
         */
        HTML,
        /**
         * Generate HTML Vulnerability report.
         */
        VULN
    }
    /**
     * The Velocity Engine.
     */
    private final VelocityEngine engine;
    /**
     * The Velocity Engine Context.
     */
    private final Context context;

    /**
     * Constructs a new ReportGenerator.
     *
     * @param applicationName the application name being analyzed
     * @param dependencies the list of dependencies
     * @param analyzers the list of analyzers used
     * @param properties the database properties (containing timestamps of the NVD CVE data)
     */
    public ReportGenerator(String applicationName, List<Dependency> dependencies, List<Analyzer> analyzers, DatabaseProperties properties) {
        engine = createVelocityEngine();
        context = createContext();

        engine.init();

        final DateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy 'at' HH:mm:ss z");
        final DateFormat dateFormatXML = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        final Date d = new Date();
        final String scanDate = dateFormat.format(d);
        final String scanDateXML = dateFormatXML.format(d);
        final EscapeTool enc = new EscapeTool();

        context.put("applicationName", applicationName);
        context.put("dependencies", dependencies);
        context.put("analyzers", analyzers);
        context.put("properties", properties);
        context.put("scanDate", scanDate);
        context.put("scanDateXML", scanDateXML);
        context.put("enc", enc);
        context.put("version", Settings.getString(Settings.KEYS.APPLICATION_VERSION, "Unknown"));
    }

    /**
     * Creates a new Velocity Engine.
     *
     * @return a velocity engine.
     */
    private VelocityEngine createVelocityEngine() {
        final VelocityEngine engine = new VelocityEngine();
        // Logging redirection for Velocity - Required by Jenkins and other server applications
        engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, VelocityLoggerRedirect.class.getName());
        return engine;
    }

    /**
     * Creates a new Velocity Context.
     *
     * @return a Velocity Context.
     */
    private Context createContext() {
        return new VelocityContext();
    }

    /**
     * Generates the Dependency Reports for the identified dependencies.
     *
     * @param outputStream the OutputStream to send the generated report to
     * @param format the format the report should be written in
     * @throws IOException is thrown when the template file does not exist
     * @throws Exception is thrown if there is an error writing out the reports.
     */
    public void generateReports(OutputStream outputStream, Format format) throws IOException, Exception {
        if (format == Format.XML || format == Format.ALL) {
            generateReport("XmlReport", outputStream);
        }
        if (format == Format.HTML || format == Format.ALL) {
            generateReport("HtmlReport", outputStream);
        }
        if (format == Format.VULN || format == Format.ALL) {
            generateReport("VulnerabilityReport", outputStream);
        }
    }

    /**
     * Generates the Dependency Reports for the identified dependencies.
     *
     * @param outputDir the path where the reports should be written
     * @param format the format the report should be written in
     * @throws IOException is thrown when the template file does not exist
     * @throws Exception is thrown if there is an error writing out the reports.
     */
    public void generateReports(String outputDir, Format format) throws IOException, Exception {
        if (format == Format.XML || format == Format.ALL) {
            generateReport("XmlReport", outputDir + File.separator + "dependency-check-report.xml");
        }
        if (format == Format.HTML || format == Format.ALL) {
            generateReport("HtmlReport", outputDir + File.separator + "dependency-check-report.html");
        }
        if (format == Format.VULN || format == Format.ALL) {
            generateReport("VulnerabilityReport", outputDir + File.separator + "dependency-check-vulnerability.html");
        }
    }

    /**
     * Generates the Dependency Reports for the identified dependencies.
     *
     * @param outputDir the path where the reports should be written
     * @param outputFormat the format the report should be written in (XML, HTML, ALL)
     * @throws IOException is thrown when the template file does not exist
     * @throws Exception is thrown if there is an error writing out the reports.
     */
    public void generateReports(String outputDir, String outputFormat) throws IOException, Exception {
        final String format = outputFormat.toUpperCase();
        final String pathToCheck = outputDir.toLowerCase();
        if (format.matches("^(XML|HTML|VULN|ALL)$")) {
            if ("XML".equalsIgnoreCase(format)) {
                if (pathToCheck.endsWith(".xml")) {
                    generateReport("XmlReport", outputDir);
                } else {
                    generateReports(outputDir, Format.XML);
                }
            }
            if ("HTML".equalsIgnoreCase(format)) {
                if (pathToCheck.endsWith(".html") || pathToCheck.endsWith(".htm")) {
                    generateReport("HtmlReport", outputDir);
                } else {
                    generateReports(outputDir, Format.HTML);
                }
            }
            if ("VULN".equalsIgnoreCase(format)) {
                if (pathToCheck.endsWith(".html") || pathToCheck.endsWith(".htm")) {
                    generateReport("VulnReport", outputDir);
                } else {
                    generateReports(outputDir, Format.VULN);
                }
            }
            if ("ALL".equalsIgnoreCase(format)) {
                generateReports(outputDir, Format.ALL);
            }
        }
    }

    /**
     * Generates a report from a given Velocity Template. The template name provided can be the name of a template
     * contained in the jar file, such as 'XmlReport' or 'HtmlReport', or the template name can be the path to a
     * template file.
     *
     * @param templateName the name of the template to load.
     * @param outputStream the OutputStream to write the report to.
     * @throws IOException is thrown when the template file does not exist.
     * @throws Exception is thrown when an exception occurs.
     */
    protected void generateReport(String templateName, OutputStream outputStream) throws IOException, Exception {
        InputStream input = null;
        String templatePath = null;
        final File f = new File(templateName);
        if (f.exists() && f.isFile()) {
            try {
                templatePath = templateName;
                input = new FileInputStream(f);
            } catch (FileNotFoundException ex) {
                LOGGER.error("Unable to generate the report, the report template file could not be found.");
                LOGGER.debug("", ex);
            }
        } else {
            templatePath = "templates/" + templateName + ".vsl";
            input = this.getClass().getClassLoader().getResourceAsStream(templatePath);
        }
        if (input == null) {
            throw new IOException("Template file doesn't exist");
        }

        final InputStreamReader reader = new InputStreamReader(input, "UTF-8");
        OutputStreamWriter writer = null;

        try {
            writer = new OutputStreamWriter(outputStream, "UTF-8");

            if (!engine.evaluate(context, writer, templatePath, reader)) {
                throw new Exception("Failed to convert the template into html.");
            }
            writer.flush();
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ex) {
                    LOGGER.trace("", ex);
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException ex) {
                    LOGGER.trace("", ex);
                }
            }
            try {
                reader.close();
            } catch (IOException ex) {
                LOGGER.trace("", ex);
            }
        }
    }

    /**
     * Generates a report from a given Velocity Template. The template name provided can be the name of a template
     * contained in the jar file, such as 'XmlReport' or 'HtmlReport', or the template name can be the path to a
     * template file.
     *
     * @param templateName the name of the template to load.
     * @param outFileName the filename and path to write the report to.
     * @throws IOException is thrown when the template file does not exist.
     * @throws Exception is thrown when an exception occurs.
     */
    protected void generateReport(String templateName, String outFileName) throws Exception {
        File outFile = new File(outFileName);
        if (outFile.getParentFile() == null) {
            outFile = new File(".", outFileName);
        }
        if (!outFile.getParentFile().exists()) {
            final boolean created = outFile.getParentFile().mkdirs();
            if (!created) {
                throw new Exception("Unable to create directory '" + outFile.getParentFile().getAbsolutePath() + "'.");
            }
        }

        OutputStream outputSteam = null;
        try {
            outputSteam = new FileOutputStream(outFile);
            generateReport(templateName, outputSteam);
        } finally {
            if (outputSteam != null) {
                try {
                    outputSteam.close();
                } catch (IOException ex) {
                    LOGGER.trace("ignore", ex);
                }
            }
        }
    }
}
