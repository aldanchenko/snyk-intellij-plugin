package io.snyk.plugin

import com.intellij.diagnostic.ReportMessages
import com.intellij.notification.{Notification, NotificationListener, NotificationType}
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationAction
import com.intellij.openapi.options.ShowSettingsUtil
import io.snyk.plugin.ui.settings.SnykConfigurable
import io.snyk.plugin.ui.state.SnykPluginState

object SnykPluginProjectComponent {
  def getInstance: SnykPluginProjectComponent =
    ApplicationManager.getApplication.getComponent(classOf[SnykPluginProjectComponent])
}

class SnykPluginProjectComponent(project: Project) extends ProjectComponent {
  val pluginState: SnykPluginState = SnykPluginState.newInstance(project)

  override def initComponent() : Unit = {
  }

  override def disposeComponent() : Unit = {
  }

  override def projectOpened(): Unit = {
    if (!pluginState.cliClient.isCliInstalled()) {
      ReportMessages.GROUP.createNotification(
        "Warning",
        """The Snyk CLI has not been installed.
          |<a href="https://support.snyk.io/hc/en-us/articles/360003812458-Getting-started-with-the-CLI">
          |See Snyk docs to help you install it.
          |</a>"""
          .stripMargin,
        NotificationType.WARNING,
        NotificationListener.URL_OPENING_LISTENER)
        .setImportant(false)
        .addAction(getSetupCliAction)
        .notify(project);
    }
  }

  private def getSetupCliAction = NotificationAction.create("Configure", (event: AnActionEvent, notification: Notification) => {
    if (!project.isDisposed) {
      ShowSettingsUtil.getInstance.showSettingsDialog(project, SnykConfigurable.DisplayName)
    }
  })

  override def projectClosed(): Unit = {
    SnykPluginState.removeForProject(project)
  }
}
