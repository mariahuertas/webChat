package es.codeurjc.webchat;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.util.Pair;

public class Chat {

	private String name;

	private ConcurrentMap<String, Pair<User, ExecutorService>> users = new ConcurrentHashMap<>(); 
	private ChatManager chatManager;

	public Chat(ChatManager chatManager, String name) {
		this.chatManager = chatManager;
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void addUser(User user) {
		ExecutorService executorService = Executors.newSingleThreadExecutor();
		Pair<User, ExecutorService> existingUser = users.putIfAbsent(user.getName(), new Pair<>(user, executorService));
		if (existingUser == null) {
			for (Pair<User, ExecutorService> userPair : users.values()) {
				if (userPair.getKey() != user) {
					userPair.getValue().submit(() -> userPair.getKey().newUserInChat(this, user));
				}
			}
		}
	}

	public void removeUser(User user) {
		users.remove(user.getName());
		for (Pair<User, ExecutorService> userPair : users.values()){
			userPair.getValue().submit(() -> userPair.getKey().userExitedFromChat(this, user));
		}
	}

	public Collection<Pair<User, ExecutorService>> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	public User getUser(String name) {
		return users.get(name).getKey(); 
	}

	public void sendMessage(User user, String message) {
		for (Pair<User, ExecutorService> userPair : users.values()){
			userPair.getValue().submit(() -> userPair.getKey().newMessage(this, user, message));
		}
	}

	public void close() {
		this.chatManager.closeChat(this);
	}
}
