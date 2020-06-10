package io.snyk.plugin.ui.settings

import java.awt.event.ActionEvent
import java.net.URL

import com.intellij.openapi.ui.{ComponentValidator, TextFieldWithBrowseButton, ValidationInfo}
import javax.swing.{JButton, JCheckBox, JComponent, JLabel, JPanel, JSplitPane, JTextField}
import java.awt.{Dimension, Insets}
import java.io.File

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.progress.{ProgressIndicator, ProgressManager, Task}
import com.intellij.openapi.project.Project
import com.intellij.ui.{DocumentAdapter, IdeBorderFactory}
import com.intellij.uiDesigner.core.{GridConstraints => UIGridConstraints, GridLayoutManager => UIGridLayoutManager}
import com.intellij.uiDesigner.core.Spacer
import com.intellij.util.io.HttpRequests
import javax.swing.event.DocumentEvent

class SettingsDialog(project: Project) {

  private val persistentState: SnykPersistentStateComponent =
    SnykPersistentStateComponent.getInstance(project)

  private val customEndpointTextField: JTextField = new JTextField()
  private val organizationTextField: JTextField = new JTextField()
  private val ignoreUnknownCACheckBox: JCheckBox = new JCheckBox()
  private val versionTextField = new JTextField()
  private val cliPathTextField = new JTextField()

  private val rootPanel: JPanel = new JPanel()

  setupUI()
  setupValidation()

  if (persistentState != null) {
    reset()
  }

  def getRootPanel: JComponent = rootPanel

  def apply(): Unit = {
    val customEndpoint = customEndpointTextField.getText

    if (!isUrlValid(customEndpoint)) {
      return
    }

    persistentState.customEndpointUrl = customEndpoint
    persistentState.organization = organizationTextField.getText
    persistentState.ignoreUnknownCA = ignoreUnknownCACheckBox.isSelected
  }

  def reset(): Unit = {
    customEndpointTextField.setText(persistentState.getCustomEndpointUrl())
    organizationTextField.setText(persistentState.getOrganization())
    ignoreUnknownCACheckBox.setSelected(persistentState.isIgnoreUnknownCA())
  }

  def isModified: Boolean =
    isCustomEndpointModified || isOrganizationModified || isIgnoreUnknownCAModified

  private def isCustomEndpointModified =
    customEndpointTextField.getText != persistentState.getCustomEndpointUrl()

  private def isOrganizationModified =
    organizationTextField.getText() != persistentState.getOrganization()

  private def isIgnoreUnknownCAModified =
    ignoreUnknownCACheckBox.isSelected != persistentState.isIgnoreUnknownCA()

  private def setupUI(): Unit = {
    val defaultTextFieldWidth = 500
    rootPanel.setLayout(
      new UIGridLayoutManager(1, 1, new Insets(0, 0, 0, 0), -1, -1))

    val mainSettingsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT)

    mainSettingsSplitPane.setOneTouchExpandable(true)
    mainSettingsSplitPane.setDividerLocation(200)
    mainSettingsSplitPane.setDividerSize(3)

    rootPanel.add(
      mainSettingsSplitPane,
      new UIGridConstraints(
        0,
        0,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
        com.intellij.uiDesigner.core.GridConstraints.FILL_BOTH,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW,
        null,
        new Dimension(200, 200),
        null,
        0,
        false
      )
    )

    val cliConfigurationPanel = new JPanel
    cliConfigurationPanel.setLayout(
      new UIGridLayoutManager(4, 3, new Insets(0, 0, 0, 0), -1, -1))
    mainSettingsSplitPane.setLeftComponent(cliConfigurationPanel)
    cliConfigurationPanel.setBorder(IdeBorderFactory.createTitledBorder("CLI Configuration"))

    val customEndpointLabel = new JLabel
    customEndpointLabel.setText("Custom Endpoint:")
    cliConfigurationPanel.add(
      customEndpointLabel,
      new UIGridConstraints(
        0,
        0,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(120, 16),
        null,
        0,
        false
      )
    )

    val cliConfigurationSpacer = new Spacer
    cliConfigurationPanel.add(
      cliConfigurationSpacer,
      new UIGridConstraints(
        3,
        0,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
        com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL,
        1,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW,
        null,
        null,
        null,
        0,
        false
      )
    )

    customEndpointTextField.setEditable(true)
    cliConfigurationPanel.add(
      customEndpointTextField,
      new UIGridConstraints(
        0,
        1,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(defaultTextFieldWidth, -1),
        null,
        0,
        false
      )
    )

    ignoreUnknownCACheckBox.setText("Ignore unknown CA")
    cliConfigurationPanel.add(
      ignoreUnknownCACheckBox,
      new UIGridConstraints(
        1,
        1,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false
      )
    )

    val organizationLabel = new JLabel
    organizationLabel.setText("Organization:")
    cliConfigurationPanel.add(
      organizationLabel,
      new UIGridConstraints(
        2,
        0,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false
      )
    )

    cliConfigurationPanel.add(
      organizationTextField,
      new UIGridConstraints(
        2,
        1,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(defaultTextFieldWidth, -1),
        null,
        0,
        false
      )
    )

    val cliSetupPanel = new JPanel
    cliSetupPanel.setLayout(
      new UIGridLayoutManager(3, 3, new Insets(0, 0, 0, 0), -1, -1))
    mainSettingsSplitPane.setRightComponent(cliSetupPanel)
    cliSetupPanel.setBorder(IdeBorderFactory.createTitledBorder("CLI Setup"))

    val versionLabel = new JLabel
    versionLabel.setText("Version:")
    cliSetupPanel.add(
      versionLabel,
      new UIGridConstraints(
        0,
        0,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(120, 16),
        null,
        0,
        false
      )
    )

    val cliSetupSpacer = new Spacer
    cliSetupPanel.add(
      cliSetupSpacer,
      new UIGridConstraints(
        2,
        0,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
        com.intellij.uiDesigner.core.GridConstraints.FILL_VERTICAL,
        1,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_WANT_GROW,
        null,
        null,
        null,
        0,
        false
      )
    )

    versionTextField.setEditable(false)
    versionTextField.setEnabled(true)
    cliSetupPanel.add(
      versionTextField,
      new UIGridConstraints(
        0,
        1,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(defaultTextFieldWidth, -1),
        null,
        0,
        false
      )
    )

    val cliPathLabel = new JLabel
    cliPathLabel.setText("Path:")
    cliSetupPanel.add(
      cliPathLabel,
      new UIGridConstraints(
        1,
        0,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.FILL_NONE,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false
      )
    )

    cliPathTextField.setText(PathManager.getPluginsPath)

    cliSetupPanel.add(
      new TextFieldWithBrowseButton(cliPathTextField),
      new UIGridConstraints(
        1,
        1,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_WEST,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(defaultTextFieldWidth, -1),
        null,
        0,
        false
      )
    )

    val downloadButton = new JButton
    downloadButton.setText("Download")
    cliSetupPanel.add(
      downloadButton,
      new UIGridConstraints(
        0,
        2,
        1,
        1,
        com.intellij.uiDesigner.core.GridConstraints.ANCHOR_CENTER,
        com.intellij.uiDesigner.core.GridConstraints.FILL_HORIZONTAL,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_SHRINK | com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_CAN_GROW,
        com.intellij.uiDesigner.core.GridConstraints.SIZEPOLICY_FIXED,
        null,
        new Dimension(115, -1),
        null,
        0,
        false
      )
    )

    downloadButton.addActionListener((actionEvent: ActionEvent) => {
      val title = "Download"

      ProgressManager.getInstance().run(new Task.Modal(project, title, true) {
        override def run(indicator: ProgressIndicator): Unit = {
          indicator.setIndeterminate(true)
          indicator.pushState()

          try {
            indicator.setText(title)

            val tagName = "v1.336.0"
            val snykWrapperFileName = "snyk-macos"

            val url = new URL(String.format("https://github.com/snyk/snyk/releases/download/%s/%s", tagName, snykWrapperFileName)).toString

            HttpRequests
              .request(url)
              .productNameAsUserAgent()
              .saveToFile(new File(cliPathTextField.getText, "snyk-macos"), indicator)

            versionTextField.setText(tagName)
          } finally {
            indicator.popState()
          }
        }
      })
    })

    customEndpointLabel.setLabelFor(customEndpointTextField)
    organizationLabel.setLabelFor(organizationTextField)

    cliPathLabel.setLabelFor(cliPathTextField)
  }

  private def setupValidation(): Unit = {
    new ComponentValidator(project)
      .withValidator(() => {

        if (isUrlValid(customEndpointTextField.getText)) {
          null
        } else {
          new ValidationInfo("Invalid custom enpoint URL.",
                             customEndpointTextField)
        }
      })
      .installOn(customEndpointTextField)

    customEndpointTextField.getDocument.addDocumentListener(
      new DocumentAdapter() {
        protected def textChanged(documentEvent: DocumentEvent): Unit =
          ComponentValidator
            .getInstance(customEndpointTextField)
            .ifPresent((componentValidator: ComponentValidator) =>
              componentValidator.revalidate())
      })
  }

  private def isUrlValid(url: String): Boolean =
    try {
      if (url.nonEmpty) {
        new URL(url).toURI

        true
      } else {
        true
      }
    } catch {
      case _: Throwable => false
    }
}
