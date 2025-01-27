package es.codeurjc.webchat;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ChatManager {

	private ConcurrentMap<String, Chat> chats = new ConcurrentHashMap<>();
	private ConcurrentMap<String, User> users = new ConcurrentHashMap<>();
	private int maxChats;
	private Semaphore chatAvailable;

	public ChatManager(int maxChats) {
		this.maxChats = maxChats;
		this.chatAvailable = new Semaphore(this.maxChats);
	}

	public void newUser(User user) {
		
		if(users.containsKey(user.getName())){
			throw new IllegalArgumentException("There is already a user with name \'"
					+ user.getName() + "\'");
		} else {
			users.putIfAbsent(user.getName(), user);
		}
	}

	public Chat newChat(String name, long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
		try {
			if (!chatAvailable.tryAcquire(timeout, unit)) {
				throw new TimeoutException("There is no enought capacity to create a new chat");
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (chats.containsKey(name)) {
			return chats.get(name);
		} else {
			Chat newChat = new Chat(this, name);
			chats.putIfAbsent(name, newChat);

			for (User user : users.values()) {
				user.newChat(newChat);
			}

			return newChat;
		}
	}

	public void closeChat(Chat chat) {
		Chat removedChat = chats.remove(chat.getName());
		
		if (removedChat == null) {
			throw new IllegalArgumentException("Trying to remove an unknown chat with name \'"
					+ chat.getName() + "\'");
		}

		for(User user : users.values()){
			user.chatClosed(removedChat);
		}
		chatAvailable.release();
	}

	public Collection<Chat> getChats() {
		return Collections.unmodifiableCollection(chats.values());
	}

	public Chat getChat(String chatName) {
		return chats.get(chatName);
	}

	public Collection<User> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	public User getUser(String userName) {
		return users.get(userName);
	}

	public void close() {}
}
