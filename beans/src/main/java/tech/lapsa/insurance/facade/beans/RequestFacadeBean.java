package tech.lapsa.insurance.facade.beans;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.Request;
import com.lapsa.insurance.domain.crm.User;

import tech.lapsa.insurance.dao.RequestDAO.RequestDAORemote;
import tech.lapsa.insurance.facade.RequestFacade;
import tech.lapsa.insurance.facade.RequestFacade.RequestFacadeLocal;
import tech.lapsa.insurance.facade.RequestFacade.RequestFacadeRemote;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.exceptions.IllegalState;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;

@Stateless(name = RequestFacade.BEAN_NAME)
public class RequestFacadeBean
	implements RequestFacadeLocal, RequestFacadeRemote {

    // EJBs

    // insurance-dao (remote)

    @EJB
    private RequestDAORemote dao;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Request commentRequest(Request request, User user, String message)
	    throws IllegalState, IllegalArgument {
	try {
	    return _commentRequest(request, user, message);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    private static final DateTimeFormatter COMMENT_DATE_TIME_FORMATTER = //
	    new DateTimeFormatterBuilder() //
		    .append(DateTimeFormatter.ISO_LOCAL_DATE) //
		    .appendLiteral(" ") //
		    .append(DateTimeFormatter.ISO_LOCAL_TIME) //
		    .toFormatter();

    private static String getTimestamp() {
	return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(COMMENT_DATE_TIME_FORMATTER);
    }

    private Request _commentRequest(Request request, User user, String message) {
	MyObjects.requireNonNull(request, "request");
	MyObjects.requireNonNull(user, "user");
	MyStrings.requireNonEmpty(message, "message");

	final String newLine = MyStrings.format("%1$s %2$s\n%3$s", //
		getTimestamp(), // 1
		user.getName(), // 2
		message // 3
	);

	final String oldNote = request.getNote();
	final String newNote = MyStrings.format("\n%1$s\n%2$s", newLine, oldNote == null ? "" : oldNote);

	request.setNote(newNote);

	final Request response;
	try {
	    response = dao.save(request);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e);
	}

	return response;
    }
}
