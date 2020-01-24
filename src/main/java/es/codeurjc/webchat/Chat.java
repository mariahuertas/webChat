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
		if (null == existingUser) {
			for (Pair<User, ExecutorService> u : users.values()) {
				if (u.getKey() != user) {
					u.getValue().submit(() -> u.getKey().newUserInChat(this, user));
				}
			}
		}
	}

	public void removeUser(User user) {
		users.remove(user.getName());
		for (Pair<User, ExecutorService> u : users.values()){
			u.getValue().submit(() -> u.getKey().userExitedFromChat(this, user));
		}
	}

	public Collection<Pair<User, ExecutorService>> getUsers() {
		return Collections.unmodifiableCollection(users.values());
	}

	public Pair<User, ExecutorService> getUser(String name) {
		return users.get(name);
	}

	public void sendMessage(User user, String message) {
		for (Pair<User, ExecutorService> u : users.values()){
			u.getValue().submit(() -> u.getKey().newMessage(this, user, message));
		}
	}

	public void close() {
		this.chatManager.closeChat(this);
	}
}
