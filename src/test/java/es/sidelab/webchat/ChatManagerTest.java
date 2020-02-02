package es.sidelab.webchat;

import static org.junit.Assert.assertTrue;
import java.util.ConcurrentModificationException;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Exchanger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.Ignore;
import org.junit.Test;

import es.codeurjc.webchat.Chat;
import es.codeurjc.webchat.ChatManager;
import es.codeurjc.webchat.User;

public class ChatManagerTest {

	@Ignore("Solved in improvement 5")
	@Test
	public void newChat() throws InterruptedException, TimeoutException {

		// Crear el chat Manager
		ChatManager chatManager = new ChatManager(5);

		// Crear un usuario que guarda en chatName el nombre del nuevo chat
		final String[] chatName = new String[1];

		chatManager.newUser(new TestUser("user") {
			public void newChat(Chat chat) {
				chatName[0] = chat.getName();
			}
		});

		// Crear un nuevo chat en el chatManager
		chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		// Comprobar que el chat recibido en el método 'newChat' se llama 'Chat'
		assertTrue("The method 'newChat' should be invoked with 'Chat', but the value is " + chatName[0],
				Objects.equals(chatName[0], "Chat"));
	}

	@Ignore("Solved in improvement 5")
	@Test
	public void newUserInChat() throws InterruptedException, TimeoutException {

		ChatManager chatManager = new ChatManager(5);

		final String[] newUser = new String[1];

		TestUser user1 = new TestUser("user1") {
			@Override
			public void newUserInChat(Chat chat, User user) {
				newUser[0] = user.getName();
			}
		};

		TestUser user2 = new TestUser("user2");

		chatManager.newUser(user1);
		chatManager.newUser(user2);

		Chat chat = chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		chat.addUser(user1);
		chat.addUser(user2);

		assertTrue("Notified new user '" + newUser[0] + "' is not equal than user name 'user2'",
				"user2".equals(newUser[0]));

	}

	@Test
	public void improvement1_verifyNoConcurrenceErrosInChatManagerAndChat()
			throws InterruptedException, TimeoutException {
		// OPCION B
		// Create 50 chats in ChatMananger
		ChatManager chatManager = new ChatManager(50);

		// 4 threads in parallel simulating 4 concurrent users
		final int numUsers = 4;

		Callable<Boolean> userThread = () -> {
			// each thread creates a TestUser object and registers it in ChatManager
			TestUser testUser = new TestUser("user_" + Thread.currentThread().getId());
			chatManager.newUser(testUser);

			// threads repeats 5 times the next actions:
			for (int i = 0; i < 5; i++) {
				// it creates a chat called "chat+iteration number (manager.newChat)
				Chat chat = chatManager.newChat("Chat_" + i, 5, TimeUnit.SECONDS);
				// it assigns an users in that chat (chat.addUser(user)
				chat.addUser(testUser);
				// it shows all the users of that chat
			}

			return true;
		};

		ExecutorService executor = Executors.newFixedThreadPool(numUsers);
		CompletionService<Boolean> completionService = new ExecutorCompletionService<>(executor);

		for (int i = 0; i < numUsers; i++) {
			completionService.submit(userThread);
		}

		for (int i = 0; i < numUsers; i++) {
			try {
				Future<Boolean> future = completionService.take();
				System.out.println("Future result is " + future.get() + "; And task done is " + future.isDone());
			} catch (ExecutionException e) {
				e.printStackTrace();
				throw new ConcurrentModificationException(e.getCause());
			}
		}
	}

	@Test
	public void improvement4_1_parallelNotifications() throws InterruptedException, TimeoutException {
		// Create 1 chat in ChatMananger
		ChatManager chatManager = new ChatManager(1);

		// chat has 4 users
		final int numUsers = 4;

		// new chat in ChatManager
		String chatName = "NewChat";
		Chat chat = chatManager.newChat(chatName, 5, TimeUnit.SECONDS);

		// Initialize CDL -> numUser
		CountDownLatch countDownLatchInit = new CountDownLatch(numUsers);

		for (int i = 0; i < numUsers; i++) {
			// creates a TestUser object and registers it in ChatManager
			TestUser testUser = new TestUser("user_" + i) {
				@Override
				public void newMessage(Chat chat, User user, String message) {
					try {
						Thread.sleep(1000);
						countDownLatchInit.countDown();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			};

			// it assigns an users in that chat (chat.addUser(user)
			chat.addUser(testUser);
		}

		User testUser = chatManager.getUser("user_1");

		// initialize count
		Long timeBeforeSendMessage = System.currentTimeMillis();

		chatManager.getChat(chatName).sendMessage(testUser, "testing time");

		// user who sends the message is waiting in an await until all the others have
		// invoked the countDown
		countDownLatchInit.await();

		// how long does it take to process the message?
		long testTime = System.currentTimeMillis() - timeBeforeSendMessage;
		long expectedTime = 1050;
		assertTrue("Total processing time is longer than expected", testTime < expectedTime);
	}

	@Test
	public void improvement4_2_messageOrder() throws InterruptedException, TimeoutException {
		ChatManager chatManager = new ChatManager(1);
		String chatName = "NewChat";
		long timeout = 5;
		Chat chat = chatManager.newChat(chatName, timeout, TimeUnit.SECONDS);

		Exchanger<Boolean> exchanger = new Exchanger<Boolean>();

		TestUser receiver = new TestUser("receiver") {
			private int expectedMessage = 1;

			@Override
			public void newMessage(Chat chat, User user, String message) {
				try {
					if (expectedMessage == 5) {
						exchanger.exchange(true);
					}
					if (Integer.parseInt(message) == expectedMessage) {
						expectedMessage++;
					} else {
						exchanger.exchange(false);
					}
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};

		TestUser sender = new TestUser("sender");
		chatManager.newUser(sender);
		chatManager.newUser(receiver);
		chat.addUser(sender);
		chat.addUser(receiver);

		for (int i = 1; i < 6; i++) {
			chatManager.getChat(chatName).sendMessage(sender, String.valueOf(i));
		}

		boolean areMessagesInOrder = exchanger.exchange(false, timeout, TimeUnit.SECONDS);
		assertTrue("The messages are not received in order ", areMessagesInOrder);
	}

	@Test
	public void improvement5_1_createNewChat() throws InterruptedException, TimeoutException {
		ChatManager chatManager = new ChatManager(1);

		// Crear un usuario que guarda en chatName el nombre del nuevo chat
		final String[] chatName = new String[1];

		chatManager.newUser(new TestUser("user") {
			@Override
			public void newChat(Chat chat) {
				chatName[0] = chat.getName();
			}
		});

		assertTrue("The number of chats is: " + chatManager.getChats().size(),
				Objects.equals(chatManager.getChats().size(), 0));

		// Crear un nuevo chat en el chatManager
		chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		// Comprobar que el chat recibido en el método 'newChat' se llama 'Chat'
		assertTrue("The method 'newChat' should be invoked with 'Chat', but the value is " + chatName[0],
				Objects.equals(chatName[0], "Chat"));

		assertTrue("The number of chats is: " + chatManager.getChats().size(),
				Objects.equals(chatManager.getChats().size(), 1));
	}

	@Test
	public void improvement5_3_newUserInChat() throws InterruptedException, TimeoutException {
		ChatManager chatManager = new ChatManager(1);

		Exchanger<String> exchanger = new Exchanger<String>();

		TestUser user1 = new TestUser("user1") {
			@Override
			public void newUserInChat(Chat chat, User user) {
				try {
					exchanger.exchange(user.getName());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};

		TestUser user2 = new TestUser("user2");
		chatManager.newUser(user1);
		chatManager.newUser(user2);

		String chatName = "NewChat";
		long timeout = 5;
		Chat chat = chatManager.newChat(chatName, timeout, TimeUnit.SECONDS);

		chat.addUser(user1);
		chat.addUser(user2);

		String testResult = exchanger.exchange("-");
		assertTrue("Notified new user '" + testResult + "' is not equal than user name 'user2'",
				"user2".equals(testResult));
	}

	@Test
	public void improvement5_2_deleteChat() throws InterruptedException, TimeoutException {
		ChatManager chatManager = new ChatManager(2);

		chatManager.newUser(new TestUser("user") {
			@Override
			public void chatClosed(Chat chat) {
			}
		});

		assertTrue("The number of chats is: " + chatManager.getChats().size(),
				Objects.equals(chatManager.getChats().size(), 0));
		
		chatManager.newChat("Chat1", 5, TimeUnit.SECONDS);
		Chat chat2 = chatManager.newChat("Chat2", 5, TimeUnit.SECONDS);

		assertTrue("The number of chats is: " + chatManager.getChats().size(),
				Objects.equals(chatManager.getChats().size(), 2));
		chatManager.closeChat(chat2);

		assertTrue("The number of chats is: " + chatManager.getChats().size(),
				Objects.equals(chatManager.getChats().size(), 1));
	}

	@Test
	public void improvement5_4_userExitedFromChat() throws InterruptedException, TimeoutException {
		ChatManager chatManager = new ChatManager(1);

		Exchanger<String> exchanger = new Exchanger<String>();

		TestUser user1 = new TestUser("user1") {
			@Override
			public void userExitedFromChat(Chat chat, User user) {
				try {
					exchanger.exchange(user.getName());
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};

		TestUser user2 = new TestUser("user2"); // this one is going to be removed
		chatManager.newUser(user1);
		chatManager.newUser(user2);

		String chatName = "NewChat";
		long timeout = 5;
		Chat chat = chatManager.newChat(chatName, timeout, TimeUnit.SECONDS);
		chat.addUser(user1);
		chat.addUser(user2);

		chat.removeUser(user2);

		String testResult = exchanger.exchange("-");
		assertTrue("Notified that user removed is '" + testResult + " and it should be user2",
				"user2".equals(testResult));
	}

	@Test
	public void improvement5_5_sendMessageToAChat() throws InterruptedException, TimeoutException {
		ChatManager chatManager = new ChatManager(1);
		String chatName = "NewChat";
		long timeout = 5;
		Chat chat = chatManager.newChat(chatName, timeout, TimeUnit.SECONDS);

		Exchanger<String> exchanger = new Exchanger<String>();

		TestUser receiver = new TestUser("receiver") {
			@Override
			public void newMessage(Chat chat, User user, String message) {
				try {
					exchanger.exchange(message);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		};

		TestUser sender = new TestUser("sender");
		chatManager.newUser(sender);
		chatManager.newUser(receiver);
		chat.addUser(sender);
		chat.addUser(receiver);

		String message = "Hi, I am " + sender.getName();
		chatManager.getChat(chatName).sendMessage(sender, message);

		String testResult = exchanger.exchange("-");
		assertTrue("Notified that message is '" + testResult + " and it should be 'Hi, I am sender",
				"Hi, I am sender".equals(testResult));
	}
	
	@Test
	public void newFunctionality_createNewChatWithTimeOut() throws InterruptedException, TimeoutException {
		ChatManager chatManager = new ChatManager(1);
		chatManager.newChat("Chat", 5, TimeUnit.SECONDS);

		Long timeBeforeCreateNewChat = System.currentTimeMillis();
		try {
			chatManager.newChat("Chat2", 5, TimeUnit.SECONDS);

		} catch (TimeoutException e) {
			long testTime = System.currentTimeMillis() - timeBeforeCreateNewChat;
			assertTrue("Total processing time is longer than expected. TestTime:" + testTime, testTime > 5);
		}	
	}
}
