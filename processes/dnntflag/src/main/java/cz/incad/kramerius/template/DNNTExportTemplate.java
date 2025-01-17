package cz.incad.kramerius.template;

import com.google.inject.Inject;
import com.google.inject.Provider;
import cz.incad.kramerius.processes.LRProcessDefinition;
import cz.incad.kramerius.processes.template.ProcessInputTemplate;
import cz.incad.kramerius.security.labels.Label;
import cz.incad.kramerius.security.labels.LabelsManager;
import cz.incad.kramerius.security.labels.LabelsManagerException;
import cz.incad.kramerius.service.ResourceBundleService;
import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.antlr.stringtemplate.language.DefaultTemplateLexer;
import org.kramerius.processes.filetree.TreeItem;
import org.kramerius.processes.filetree.TreeModelFilter;
import org.kramerius.processes.utils.TreeModelUtils;

import java.io.*;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DNNTExportTemplate implements ProcessInputTemplate {

    public static final Logger LOGGER = Logger.getLogger(DNNTExportTemplate.class.getName());

    @Inject
    ResourceBundleService resourceBundleService;

    @Inject
    Provider<Locale> localeProvider;

    @Inject
    LabelsManager labelsManager;

    @Override
    public void renderInput(LRProcessDefinition definition, Writer writer, Properties paramsMapping) throws IOException {
        try {
            InputStream iStream = this.getClass().getResourceAsStream("parametrizedexportdnnt.st");
            StringTemplateGroup templateGroup = new StringTemplateGroup(new InputStreamReader(iStream,"UTF-8"), DefaultTemplateLexer.class);
            StringTemplate template = templateGroup.getInstanceOf("form");
            ResourceBundle resbundle = resourceBundleService.getResourceBundle("labels", localeProvider.get());

            template.setAttribute("bundle", AbstractDNNTCSVInputTemplate.resourceBundleMap(resbundle));
            template.setAttribute("process", "parametrizeddnntexport");
            template.setAttribute("allLabels", labelsManager.getLabels().stream().map(Label::getName).collect(Collectors.toList()));

            writer.write(template.toString());
        } catch (LabelsManagerException e) {
            LOGGER.log(Level.SEVERE,e.getMessage(),e);
        }
    }
}
