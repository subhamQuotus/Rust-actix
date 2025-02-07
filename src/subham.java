package org.intellij.sdk.language.deployment;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

public class RunCardanoNodeAction extends AnAction implements Disposable {
    private OSProcessHandler processHandler;
    private BufferedWriter processInput;

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        // Show a dialog for user input
        JPanel panel = new JPanel(new GridLayout(0, 1));
        JTextField topologyPathField = new JTextField();
        JTextField databasePathField = new JTextField();
        JTextField socketPathField = new JTextField();
        JTextField portField = new JTextField("3001"); // Default port
        JTextField configPathField = new JTextField();

        panel.add(new JLabel("Topology File Path:"));
        panel.add(topologyPathField);
        panel.add(new JLabel("Database Path:"));
        panel.add(databasePathField);
        panel.add(new JLabel("Socket Path:"));
        panel.add(socketPathField);
        panel.add(new JLabel("Port:"));
        panel.add(portField);
        panel.add(new JLabel("Config File Path:"));
        panel.add(configPathField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Cardano Node Configuration", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return; // Exit if user cancels
        }

        String topologyPath = topologyPathField.getText().trim();
        String databasePath = databasePathField.getText().trim();
        String socketPath = socketPathField.getText().trim();
        String port = portField.getText().trim();
        String configPath = configPathField.getText().trim();

        if (topologyPath.isEmpty() || databasePath.isEmpty() || socketPath.isEmpty() || port.isEmpty() || configPath.isEmpty()) {
            Messages.showErrorDialog("All fields are required.", "Error");
            return;
        }

        // Find or create the ToolWindow
        ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Cardano Node Terminal");
        if (toolWindow == null) {
            toolWindow = ToolWindowManager.getInstance(project).registerToolWindow("Cardano Node Terminal", true, ToolWindowAnchor.BOTTOM);
        }

        ToolWindow finalToolWindow = toolWindow;

        // Run the command in a background thread
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            GeneralCommandLine commandLine = new GeneralCommandLine("cardano-node", "run",
                    "--topology", topologyPath,
                    "--database-path", databasePath,
                    "--socket-path", socketPath,
                    "--port", port,
                    "--config", configPath);
            commandLine.setWorkDirectory(project.getBasePath());

            try {
                processHandler = new OSProcessHandler(commandLine);
                processInput = new BufferedWriter(new OutputStreamWriter(processHandler.getProcessInput()));

                ApplicationManager.getApplication().invokeLater(() -> {
                    // Initialize ConsoleView for the terminal
                    ConsoleView consoleView = new ConsoleViewImpl(project, true);

                    // Create the terminal panel
                    JBPanel<?> terminalPanel = createTerminalPanel(consoleView);

                    // Add the panel to the ToolWindow
                    Content content = ContentFactory.getInstance().createContent(
                            terminalPanel,
                            "Cardano Node Terminal",
                            false
                    );
                    finalToolWindow.getContentManager().addContent(content);

                    consoleView.clear();
                    consoleView.attachToProcess(processHandler);
                });

                processHandler.startNotify();

            } catch (ExecutionException ex) {
                ApplicationManager.getApplication().invokeLater(() ->
                        Messages.showErrorDialog("Error starting Cardano Node: " + ex.getMessage(), "Error"));
            }
        });
    }

    private JBPanel<?> createTerminalPanel(ConsoleView consoleView) {
        JBPanel<?> terminalPanel = new JBPanel<>(new BorderLayout());
        terminalPanel.add(consoleView.getComponent(), BorderLayout.CENTER);
        return terminalPanel;
    }

    @Override
    public void dispose() {
        // Dispose resources
        try {
            if (processInput != null) {
                processInput.close();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (processHandler != null && !processHandler.isProcessTerminated()) {
            processHandler.destroyProcess();
        }
    }
}

