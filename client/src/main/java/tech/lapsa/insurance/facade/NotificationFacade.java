package tech.lapsa.insurance.facade;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.ejb.Local;
import javax.ejb.Remote;

import com.lapsa.insurance.domain.Request;

import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;

public interface NotificationFacade {

    @Local
    public interface NotificationFacadeLocal extends NotificationFacade {
    }

    @Remote
    public interface NotificationFacadeRemote extends NotificationFacade {
    }

    void send(Notification notification) throws IllegalArgumentException;

    public static final class Notification implements Serializable {

	private static final long serialVersionUID = 1L;

	public static enum NotificationChannel {
	    EMAIL, PUSH, SMS;
	}

	public static enum NotificationRecipientType {
	    COMPANY, REQUESTER;
	}

	public static enum NotificationEventType {
	    NEW_REQUEST, REQUEST_PAID;
	}

	public static NotificationBuilder builder() {
	    return new NotificationBuilder();
	}

	public static final class NotificationBuilder {

	    private NotificationChannel channel;
	    private NotificationRecipientType recipientType;
	    private NotificationEventType event;
	    private Request entity;
	    private Map<String, String> properties = new HashMap<>();

	    private NotificationBuilder() {
	    }

	    public NotificationBuilder withChannel(final NotificationChannel channel) {
		this.channel = MyObjects.requireNonNull(channel, "channel");
		return this;
	    }

	    public NotificationBuilder withRecipient(final NotificationRecipientType recipientType) {
		this.recipientType = MyObjects.requireNonNull(recipientType, "recipientType");
		return this;
	    }

	    public NotificationBuilder withEvent(final NotificationEventType event) {
		this.event = MyObjects.requireNonNull(event, "event");
		return this;
	    }

	    public NotificationBuilder forEntity(final Request entity) {
		this.entity = MyObjects.requireNonNull(entity, "entity");
		return this;
	    }

	    public NotificationBuilder withProperty(final String key, final String value) {
		properties.put(MyStrings.requireNonEmpty(key, "key"), MyStrings.requireNonEmpty(value, "value"));
		return this;
	    }

	    public Notification build() {
		return new Notification(channel, recipientType, event, entity, properties);
	    }

	}

	private final NotificationChannel channel;
	private final NotificationRecipientType recipientType;
	private final NotificationEventType event;
	private final Request entity;
	private final Map<String, String> propsMap;

	private Notification(NotificationChannel channel, NotificationRecipientType recipientType,
		NotificationEventType event, Request entity, Map<String, String> propsMap) {
	    this.channel = MyObjects.requireNonNull(channel, "channel");
	    this.recipientType = MyObjects.requireNonNull(recipientType, "recipientType");
	    this.event = MyObjects.requireNonNull(event, "event");
	    this.entity = MyObjects.requireNonNull(entity, "entity");
	    this.propsMap = Collections.unmodifiableMap(MyObjects.requireNonNull(propsMap, "propsMap"));
	}

	public NotificationChannel getChannel() {
	    return channel;
	}

	public NotificationRecipientType getRecipientType() {
	    return recipientType;
	}

	public NotificationEventType getEvent() {
	    return event;
	}

	public Request getEntity() {
	    return entity;
	}

	public Properties getProperties() {
	    if (propsMap.isEmpty())
		return null;
	    final Properties p = new Properties();
	    propsMap.entrySet().stream() //
		    .forEach(x -> p.setProperty(x.getKey(), x.getValue()));
	    return p;
	}
    }
}
