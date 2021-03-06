package com.eyelinecom.whoisd.sads2.plugins.bitrix.services.messaging;

import com.eyelinecom.whoisd.sads2.plugins.bitrix.PluginContext;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.model.app.Application;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.model.chat.Chat;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.model.operator.Operator;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.model.queue.Queue;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.model.user.User;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.services.bitrix.BitrixApiProvider;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.services.db.dao.ChatDAO;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.utils.EncodingUtils;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.utils.TemplateUtils;
import com.eyelinecom.whoisd.sads2.plugins.bitrix.utils.PrettyPrintUtils;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * author: Artem Voronov
 */
public class MessageDeliveryService implements MessageDeliveryProvider {

  private final BitrixApiProvider bitrixApiProvider;
  private final ChatDAO chatDAO;

  public MessageDeliveryService(BitrixApiProvider bitrixApiProvider, ChatDAO chatDAO) {
    this.bitrixApiProvider = bitrixApiProvider;
    this.chatDAO = chatDAO;
  }

  private static final Logger logger = Logger.getLogger("BITRIX_PLUGIN");
  private static final Logger loggerMessagingSads = Logger.getLogger("BITRIX_PLUGIN_MESSAGING_WITH_SADS");

  @Override
  public void sendMessageToUser(Queue queue, String message) {
    if (queue == null)
      return;

    pushMessageToUser(queue, message);
  }

  @Override
  public void sendMessageToOperator(Operator operator, String message) {
    if (operator == null)
      return;

    final Application application = operator.getApplication();
    pushMessageToBitrix(application, operator.getDialogId(), message);
  }

  @Override
  public void sendImageToOperator(Operator operator, String title, String imageUrl) {
    if (operator == null)
      return;

    final Application application = operator.getApplication();
    pushImageToBitrix(application, operator.getDialogId(), title, imageUrl);
  }

  @Override
  public void sendMessageToChat(Application application, String dialogId, String message) {
    pushMessageToBitrix(application, dialogId, message);
  }

  @Override
  public void sendMessageToAllChats(Application application, String message) {
    List<Chat> chats = chatDAO.find(application, Chat.Type.GROUP);

    for (Chat chat : chats) {
      pushMessageToBitrix(application, chat.getDialogId(), message);
    }
  }

  private void pushMessageToBitrix(Application application, String dialogId, String message) {
    bitrixApiProvider.sendMessage(application, dialogId, message);
  }

  private void pushImageToBitrix(Application application, String dialogId, String title, String imageUrl) {
    bitrixApiProvider.sendImage(application, dialogId, title, imageUrl);
  }

  private static void pushMessageToUser(Queue queue, String message) {
    User user = queue.getUser();
    final String encodedDocument = createPushMessage(queue, message);
    final String pushUrl = TemplateUtils.createPushUrl(queue.getServiceId(), user.getUserId(), queue.getProtocol(), encodedDocument);

    try {
      URL url = new URL(pushUrl);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      int responseCode = connection.getResponseCode();

      if(responseCode != 200)
        logger.error("Unable push message. User ID: " + user.getUserId() + ". Service: " + queue.getServiceId() + ". Protocol: " + queue.getProtocol());

    } catch (IOException ex) {
      logger.error("Unable push message", ex);
    }
  }

  private static String createPushMessage(Queue queue, String message) {
    Application application = queue.getApplication();
    final String domain = application.getDomain();
    final String lang = queue.getLanguage();
    String deployUrl = PluginContext.getInstance().getPluginUrl();
    String escapedRedirectBackBtnUrl = queue.getBackPage();
    final String encodedAndEscapedRedirectBackBtnUrl = EncodingUtils.encode(escapedRedirectBackBtnUrl);
    final String inputUrl = TemplateUtils.createInputUrl(deployUrl, domain, lang, encodedAndEscapedRedirectBackBtnUrl);
    final String escapedInputUrl = EncodingUtils.escape(inputUrl);
    String xml = TemplateUtils.createBasicPage(message, escapedInputUrl, encodedAndEscapedRedirectBackBtnUrl, lang);

    if (loggerMessagingSads.isDebugEnabled())
      loggerMessagingSads.debug("PUSHED XML:\n" + PrettyPrintUtils.toPrettyXml(xml));

    return EncodingUtils.encode(xml);
  }
}
