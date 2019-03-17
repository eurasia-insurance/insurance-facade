package tech.lapsa.insurance.facade.beans;

import java.security.Principal;
import java.util.List;
import java.util.regex.Pattern;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.crm.User;
import com.lapsa.insurance.domain.crm.UserLogin;

import tech.lapsa.insurance.dao.UserDAO.UserDAORemote;
import tech.lapsa.insurance.facade.UserFacade;
import tech.lapsa.insurance.facade.UserFacade.UserFacadeLocal;
import tech.lapsa.insurance.facade.UserFacade.UserFacadeRemote;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.logging.MyLogger;
import tech.lapsa.patterns.dao.NotFound;

@Stateless(name = UserFacade.BEAN_NAME)
public class UserFacadeBean implements UserFacadeLocal, UserFacadeRemote {

    @EJB
    private UserDAORemote userDAO;

    // READERS

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<User> getWhoEverCreatedRequests() {
        return userDAO.findAllWhoEverCreatedRequest();
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<User> getWhoEverPickedRequests() {
        return userDAO.findAllWhoEverPickedRequest();
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<User> getWhoEverCompletedRequests() {
        return userDAO.findAllWhoEverCompleteRequest();
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<User> getAll() {
        return userDAO.findAll();
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<User> getAllVisible() {
        return userDAO.findVisible();
    }

    // MODIFIERS

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public User findOrCreate(final String principalName) throws IllegalArgument {
        try {
            return _findOrCreate(principalName);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgument(e);
        }
    }

    @Override
    public User findOrCreate(final Principal principal) throws IllegalArgument {
        try {
            return _findOrCreate(principal);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgument(e);
        }
    }

    private User _findOrCreate(final Principal principal) throws IllegalArgumentException {
        MyObjects.requireNonNull(principal, "principal");
        return _findOrCreate(principal.getName());
    }

    private User _findOrCreate(final String principalName) throws IllegalArgumentException {
        MyStrings.requireNonEmpty(principalName, "principalName");
        try {
            return userDAO.getByLogin(principalName);
        } catch (final IllegalArgument e) {
            // it should not happens
            throw new EJBException(e.getMessage());
        } catch (final NotFound e) {
            logger.INFO.log("New User creating '%1$s'", principalName);

            final User uNew;
            {
                uNew = new User();
                final UserLogin login = uNew.addLogin(new UserLogin());
                login.setName(principalName);

                if (Util.isEmail(principalName)) {
                    uNew.setEmail(principalName);
                    uNew.setName(Util.stripEmailToName(principalName));
                } else
                    uNew.setName(principalName);
            }

            final User u;
            try {
                u = userDAO.save(uNew);
            } catch (final IllegalArgument e1) {
                // it should not happens
                throw new EJBException(e.getMessage());
            }

            return u;
        }
    }

    // PRIVATE

    private final MyLogger logger = MyLogger.newBuilder() //
            .withNameOf(UserFacade.class) //
            .build();

    private static class Util {

        private static final String EMAIL_PATTERN = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@"
                + "[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
        private static final Pattern pattern = Pattern.compile(EMAIL_PATTERN);

        public static boolean isEmail(final String principalName) {
            return pattern.matcher(principalName).matches();
        }

        public static String stripEmailToName(final String email) {
            if (email == null)
                return null;
            final String[] verbs = email.split("\\@")[0].split("[\\.\\s]");
            final StringBuffer sb = new StringBuffer();
            for (int i = 0; i < verbs.length; i++) {
                final String verb = verbs[i];
                if (verb.length() == 0)
                    continue;
                sb.append(Character.toUpperCase(verb.charAt(0)));
                if (verb.length() > 1)
                    sb.append(verb.substring(1));
                if (i < verbs.length - 1)
                    sb.append(" ");
            }
            return sb.toString();
        }
    }

    @Override
    public User getRootUser() {
        try {
            return userDAO.getById(0);
        } catch (IllegalArgument | NotFound e) {
            throw new EJBException("Fatal error System user not found");
        }
    }
}
