package tech.lapsa.insurance.facade.beans;

import static com.lapsa.insurance.elements.InsuranceRequestStatus.PENDING;
import static com.lapsa.insurance.elements.InsuranceRequestStatus.POLICY_ISSUED;
import static com.lapsa.insurance.elements.InsuranceRequestStatus.PREMIUM_PAID;
import static com.lapsa.insurance.elements.InsuranceRequestStatus.REQUEST_CANCELED;
import static com.lapsa.insurance.elements.InsuranceRequestStatus.PAYMENT_CANCELED;
import static com.lapsa.insurance.elements.ProgressStatus.FINISHED;
import static com.lapsa.insurance.elements.ProgressStatus.NEW;

import java.time.Instant;
import java.util.Arrays;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.InsuranceRequest;
import com.lapsa.insurance.domain.RequesterData;
import com.lapsa.insurance.domain.crm.User;
import com.lapsa.insurance.elements.InsuranceRequestCancellationReason;
import com.lapsa.insurance.elements.InsuranceRequestStatus;
import com.lapsa.international.localization.LocalizationLanguage;
import com.lapsa.international.phone.PhoneNumber;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.facade.EpaymentFacade.EpaymentFacadeRemote;
import tech.lapsa.epayment.facade.InvoiceNotFound;
import tech.lapsa.insurance.dao.InsuranceRequestDAO.InsuranceRequestDAORemote;
import tech.lapsa.insurance.dao.UserDAO.UserDAORemote;
import tech.lapsa.insurance.facade.InsuranceRequestFacade;
import tech.lapsa.insurance.facade.InsuranceRequestFacade.InsuranceRequestFacadeLocal;
import tech.lapsa.insurance.facade.InsuranceRequestFacade.InsuranceRequestFacadeRemote;
import tech.lapsa.insurance.facade.NotificationFacade.Notification;
import tech.lapsa.insurance.facade.NotificationFacade.Notification.NotificationBuilder;
import tech.lapsa.insurance.facade.NotificationFacade.Notification.NotificationChannel;
import tech.lapsa.insurance.facade.NotificationFacade.Notification.NotificationEventType;
import tech.lapsa.insurance.facade.NotificationFacade.Notification.NotificationRecipientType;
import tech.lapsa.insurance.facade.NotificationFacade.NotificationFacadeLocal;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.exceptions.IllegalState;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyNumbers;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyOptionals;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.logging.MyLogger;
import tech.lapsa.kz.taxpayer.TaxpayerNumber;
import tech.lapsa.patterns.dao.NotFound;

@Stateless(name = InsuranceRequestFacade.BEAN_NAME)
public class InsuranceRequestFacadeBean implements InsuranceRequestFacadeLocal, InsuranceRequestFacadeRemote {

    // READERS

    @Override
    public <T extends InsuranceRequest> T getById(Integer id) throws IllegalState, IllegalArgument {
	try {
	    return _getById(id);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    // MODIFIERS

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T requestReceived(final T insuranceRequest) throws IllegalArgument {
	try {
	    return _requestReceived(insuranceRequest);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T policyIssued(T insuranceRequest, String agreementNumber)
	    throws IllegalArgument, IllegalState {
	try {
	    return _policyIssued(insuranceRequest, agreementNumber);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T policyIssuedAndInvoiceCreated(T insuranceRequest,
	    String agreementNumber,
	    String invoicePayeeName,
	    Currency invoiceCurrency,
	    LocalizationLanguage invoiceLanguage,
	    String invoicePayeeEmail,
	    PhoneNumber invoicePayeePhone,
	    TaxpayerNumber invoicePayeeTaxpayerNumber,
	    String invoiceProductName,
	    Double invoiceAmount,
	    Integer invoiceQuantity) throws IllegalArgument, IllegalState {
	try {
	    final T ir1 = _policyIssued(insuranceRequest, agreementNumber);
	    final T ir2 = _invoiceCreated(ir1,
		    invoicePayeeName,
		    invoiceCurrency,
		    invoiceLanguage,
		    invoicePayeeEmail,
		    invoicePayeePhone,
		    invoicePayeeTaxpayerNumber,
		    invoiceProductName,
		    invoiceAmount,
		    invoiceQuantity);
	    return ir2;
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T policyIssuedAndPremiumPaid(T insuranceRequest,
	    User completedBy,
	    String agreementNumber,
	    String paymentMethodName,
	    Double paymentAmount,
	    Currency paymentCurrency,
	    Instant paymentInstant,
	    String paymentCard,
	    String paymentCardBank,
	    String paymentReference,
	    String payerName) throws IllegalState, IllegalArgument {
	try {
	    final T ir1 = _policyIssued(insuranceRequest, agreementNumber);
	    final T ir2 = _premiumPaid(ir1,
		    paymentMethodName,
		    paymentInstant,
		    paymentAmount,
		    paymentCurrency,
		    paymentCard,
		    paymentCardBank,
		    paymentReference,
		    payerName,
		    completedBy);
	    return ir2;
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T invoiceCreated(T insuranceRequest,
	    String invoicePayeeName,
	    Currency invoiceCurrency,
	    LocalizationLanguage invoiceLanguage,
	    String invoicePayeeEmail,
	    PhoneNumber invoicePayeePhone,
	    TaxpayerNumber invoicePayeeTaxpayerNumber,
	    String invoiceProductName, Double invoiceAmount,
	    Integer invoiceQuantity) throws IllegalArgument, IllegalState {
	try {
	    return _invoiceCreated(insuranceRequest,
		    invoicePayeeName,
		    invoiceCurrency,
		    invoiceLanguage,
		    invoicePayeeEmail,
		    invoicePayeePhone,
		    invoicePayeeTaxpayerNumber,
		    invoiceProductName,
		    invoiceAmount,
		    invoiceQuantity);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T premiumPaid(T insuranceRequest,
	    String paymentMethodName,
	    Instant paymentInstant,
	    Double paymentAmount,
	    Currency paymentCurrency,
	    String paymentCard,
	    String paymentCardBank,
	    String paymentReference,
	    String payerName,
	    User completedBy) throws IllegalArgument, IllegalState {
	try {
	    return _premiumPaid(insuranceRequest,
		    paymentMethodName,
		    paymentInstant,
		    paymentAmount,
		    paymentCurrency,
		    paymentCard,
		    paymentCardBank,
		    paymentReference,
		    payerName,
		    completedBy);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (final IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T invoicePaidByUs(T insuranceRequest,
	    String paymentMethodName,
	    Instant paymentInstant,
	    Double paymentAmount,
	    Currency paymentCurrency,
	    String paymentCard,
	    String paymentCardBank,
	    String paymentReference,
	    String payerName,
	    User completedBy) throws IllegalArgument, IllegalState {
	try {
	    final T ir1 = _markInvoicePaid(insuranceRequest, paymentInstant);
	    final T ir2 = _premiumPaid(ir1,
		    paymentMethodName,
		    paymentInstant,
		    paymentAmount,
		    paymentCurrency,
		    paymentCard,
		    paymentCardBank,
		    paymentReference,
		    payerName,
		    completedBy);
	    return ir2;
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (final IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void invoicePaidByTheir(Integer id,
	    String paymentMethodName,
	    Instant paymentInstant,
	    Double paymentAmount,
	    Currency paymentCurrency,
	    String paymentCard,
	    String paymentCardBank,
	    String paymentReference,
	    String payerName) throws IllegalArgument {
	try {
	    _premiumPaidById(id,
		    paymentMethodName,
		    paymentInstant,
		    paymentAmount,
		    paymentCurrency,
		    paymentCard,
		    paymentCardBank,
		    paymentReference,
		    payerName);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T requestCanceled(T insuranceRequest, User completedBy,
	    InsuranceRequestCancellationReason insuranceRequestCancellationReason)
	    throws IllegalState, IllegalArgument {
	try {
	    return _requestCanceled(insuranceRequest, completedBy, insuranceRequestCancellationReason);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T paymentCanceled(T insuranceRequest,
	    User completedBy,
	    InsuranceRequestCancellationReason insuranceRequestCancellationReason,
	    String comments)
	    throws IllegalState, IllegalArgument {
	try {
	    return _paymentCanceled(insuranceRequest, insuranceRequestCancellationReason, comments);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    // PRIVATE

    @EJB
    private NotificationFacadeLocal notifications;

    @EJB
    private InsuranceRequestDAORemote dao;

    private <T extends InsuranceRequest> T _requestReceived(final T insuranceRequest) throws IllegalArgumentException {

	MyObjects.requireNonNull(insuranceRequest, "insuranceRequest");

	if (insuranceRequest.getCreated() == null)
	    insuranceRequest.setCreated(Instant.now());

	if (insuranceRequest.getProgressStatus() == null)
	    insuranceRequest.setProgressStatus(NEW);

	insuranceRequest.setInsuranceRequestStatus(PENDING);

	final T ir1;
	try {
	    ir1 = dao.save(insuranceRequest);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	final NotificationBuilder builder = Notification.builder() //
		.withEvent(NotificationEventType.NEW_REQUEST) //
		.forEntity(ir1);

	switch (ir1.getType()) {
	case ONLINE:
	case EXPRESS:
	    try {
		notifications.send(builder.withChannel(NotificationChannel.EMAIL) //
			.withRecipient(NotificationRecipientType.COMPANY) //
			.build());
	    } catch (final IllegalArgument e) {
		// it should not happen
		throw new EJBException(e.getMessage());
	    }
	    if (insuranceRequest.getRequester().getEmail() != null)
		try {
		    notifications.send(builder.withChannel(NotificationChannel.EMAIL) //
			    .withRecipient(NotificationRecipientType.REQUESTER) //
			    .build());
		} catch (final IllegalArgument e) {
		    // it should not happen
		    throw new EJBException(e.getMessage());
		}
	case UNCOMPLETE:
	}

	logger.INFO.log("New %4$s accepded from '%1$s' '<%2$s>' tel '%3$s' ", //
		ir1.getRequester().getName(), // 1
		ir1.getRequester().getEmail(), // 2
		ir1.getRequester().getPhone(), // 3
		ir1.getClass().getSimpleName() // 4
	);

	return ir1;
    }

    private <T extends InsuranceRequest> T _requestCanceled(T insuranceRequest,
	    User completedBy,
	    InsuranceRequestCancellationReason insuranceRequestCancellationReason)
	    throws IllegalStateException, IllegalArgumentException {

	MyObjects.requireNonNull(insuranceRequest, "insuranceRequest");
	MyObjects.requireNonNull(completedBy, "completedBy");
	MyObjects.requireNonNull(insuranceRequestCancellationReason, "insuranceRequestCancellationReason");

	requireInStatus(insuranceRequest, PENDING, POLICY_ISSUED, PAYMENT_CANCELED);

	insuranceRequest.setProgressStatus(FINISHED);
	insuranceRequest.setCompleted(Instant.now());
	insuranceRequest.setCompletedBy(completedBy);

	insuranceRequest.setInsuranceRequestStatus(REQUEST_CANCELED);
	insuranceRequest.setInsuranceRequestCancellationReason(insuranceRequestCancellationReason);
	insuranceRequest.setAgreementNumber(null);

	final T ir1;
	try {
	    ir1 = dao.save(insuranceRequest);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e);
	}

	_cancelInvoice(ir1);

	return ir1;
    }

    @EJB
    private UserDAORemote userDao;

    private void _premiumPaidById(Integer id,
	    String paymentMethodName,
	    Instant paymentInstant,
	    Double paymentAmount,
	    Currency paymentCurrency,
	    String paymentCard,
	    String paymentCardBank,
	    String paymentReference,
	    String payerName) throws IllegalArgumentException, IllegalStateException {

	MyObjects.requireNonNull(id, "id");

	final InsuranceRequest insuranceRequest;
	try {
	    insuranceRequest = dao.getById(id);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e.getMessage());
	} catch (NotFound e) {
	    throw new IllegalArgumentException(e.getMessage());
	}

	final User completedBy;
	try {
	    completedBy = userDao.getById(0);
	} catch (IllegalArgument | NotFound e) {
	    // it should not happen
	    throw new EJBException(e.getMessage());
	}

	_premiumPaid(insuranceRequest,
		paymentMethodName,
		paymentInstant,
		paymentAmount,
		paymentCurrency,
		paymentCard,
		paymentCardBank,
		paymentReference,
		payerName,
		completedBy);
    }

    private <T extends InsuranceRequest> T _premiumPaid(final T insuranceRequest,
	    final String paymentMethodName,
	    final Instant paymentInstant,
	    final Double paymentAmount,
	    final Currency paymentCurrency,
	    final String paymentCard,
	    final String paymentCardBank,
	    final String paymentReference,
	    final String payerName,
	    final User completedBy)
	    throws IllegalArgumentException, IllegalStateException {

	MyObjects.requireNonNull(insuranceRequest, "insuranceRequest");
	MyStrings.requireNonEmpty(paymentMethodName, "paymentMethodName");
	MyObjects.requireNonNull(paymentInstant, "paymentInstant");
	MyNumbers.requirePositive(paymentAmount, "paymentAmount");
	MyObjects.requireNonNull(paymentCurrency, "paymentCurrency");
	MyObjects.requireNonNull(completedBy, "completedBy");

	requireInStatus(insuranceRequest, POLICY_ISSUED);

	insuranceRequest.setProgressStatus(FINISHED);
	insuranceRequest.setCompleted(paymentInstant);
	insuranceRequest.setCompletedBy(completedBy);

	insuranceRequest.setInsuranceRequestStatus(PREMIUM_PAID);

	insuranceRequest.setMethodName(paymentMethodName);
	insuranceRequest.setAmount(paymentAmount);
	insuranceRequest.setCurrency(paymentCurrency);
	insuranceRequest.setCard(paymentCard);
	insuranceRequest.setCardBank(paymentCardBank);
	insuranceRequest.setReference(paymentReference);
	insuranceRequest.setInstant(paymentInstant);
	insuranceRequest.setPayerName(payerName);

	final T ir1;
	try {
	    ir1 = dao.save(insuranceRequest);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	ir1.unlazy();

	try {
	    notifications.send(Notification.builder() //
		    .withEvent(NotificationEventType.REQUEST_PAID) //
		    .withChannel(NotificationChannel.EMAIL) //
		    .forEntity(ir1) //
		    .withRecipient(NotificationRecipientType.COMPANY) //
		    .build());
	} catch (final IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e.getMessage());
	}

	return ir1;
    }

    @EJB
    private EpaymentFacadeRemote epayments;

    private <T extends InsuranceRequest> T _markInvoicePaid(T insuranceRequest, Instant paymentInstant) {
	final String invoiceNumber = insuranceRequest.getInvoiceNumber();
	if (MyStrings.nonEmpty(invoiceNumber)) {
	    try {
		epayments.markInvoiceAsPaid(invoiceNumber, paymentInstant);
	    } catch (IllegalArgument | IllegalState | InvoiceNotFound e) {
		// it should not happen
		throw new EJBException(e);
	    }
	}
	return insuranceRequest;
    }

    private <T extends InsuranceRequest> T _cancelInvoice(final T insuranceRequest) {
	final String invoiceNumber = insuranceRequest.getInvoiceNumber();
	if (MyStrings.nonEmpty(invoiceNumber))
	    try {
		epayments.expireInvoice(invoiceNumber);
	    } catch (IllegalArgument | IllegalState | InvoiceNotFound e) {
		// it should not happen
		throw new EJBException(e);
	    }
	return insuranceRequest;
    }

    private <T extends InsuranceRequest> T _paymentCanceled(T insuranceRequest,
	    InsuranceRequestCancellationReason insuranceRequestCancellationReason,
	    String comments) throws IllegalArgumentException {

	requireInStatus(insuranceRequest, PREMIUM_PAID);
	
	final String invoiceNumber = insuranceRequest.getInvoiceNumber();
	if (MyStrings.nonEmpty(invoiceNumber)) {

	    final Optional<Locale> locale = MyOptionals.of(insuranceRequest)
		    .map(InsuranceRequest::getRequester)
		    .map(RequesterData::getPreferLanguage)
		    .map(LocalizationLanguage::getLocale);

	    final String reason = (locale.isPresent()
		    ? insuranceRequestCancellationReason.regular(locale.get())
		    : insuranceRequestCancellationReason.regular())
		    + " : " + comments;

	    try {
		epayments.cancelPayment(invoiceNumber, reason);
	    } catch (IllegalArgument | IllegalState | InvoiceNotFound e) {
		// it should not happen
		throw new EJBException(e);
	    }
	}
	
	insuranceRequest.setInsuranceRequestStatus(PAYMENT_CANCELED);

	final T ir1;
	try {
	    ir1 = dao.save(insuranceRequest);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return ir1;
    }

    private <T extends InsuranceRequest> T _invoiceCreated(final T insuranceRequest,
	    String invoicePayeeName,
	    Currency invoiceCurrency,
	    LocalizationLanguage invoiceLanguage,
	    String invoicePayeeEmail,
	    PhoneNumber invoicePayeePhone,
	    TaxpayerNumber invoicePayeeTaxpayerNumber,
	    String invoiceProductName,
	    Double invoiceAmount,
	    Integer invoiceQuantity)
	    throws IllegalArgumentException, IllegalStateException {

	MyObjects.requireNonNull(insuranceRequest, "insuranceRequest");
	MyObjects.requireNonNull(insuranceRequest.getId(), "insuranceRequest.id");

	requireInStatus(insuranceRequest, POLICY_ISSUED);

	final InvoiceBuilder builder = Invoice.builder() //
		.withGeneratedNumber() //
		.withConsumerName(MyStrings.requireNonEmpty(invoicePayeeName, "invoicePayeeName")) //
		.withCurrency(MyObjects.requireNonNull(invoiceCurrency, "invoiceCurrency")) //
		.withConsumerPreferLanguage(MyObjects.requireNonNull(invoiceLanguage, "invoiceLanguage"))
		//
		.withExternalId(insuranceRequest.getId()) //
		.withConsumerEmail(MyStrings.requireNonEmpty(invoicePayeeEmail, "invoicePayeeEmail")) //
		.withConsumerPhone(MyObjects.requireNonNull(invoicePayeePhone, "invoicePayeePhone")) //
		.withConsumerTaxpayerNumber(
			MyObjects.requireNonNull(invoicePayeeTaxpayerNumber, "invoicePayeeTaxpayerNumber")) //
		.withItem(MyStrings.requireNonEmpty(invoiceProductName, "invoiceProductName"),
			MyNumbers.requirePositive(invoiceQuantity, "invoiceQuantity"),
			MyNumbers.requirePositive(invoiceAmount, "invoiceAmount"));

	final Invoice invoice;
	try {
	    invoice = epayments.invoiceAccept(builder);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	insuranceRequest.setInvoiceProductName(invoiceProductName);

	insuranceRequest.setInvoiceQuantity(invoiceQuantity);
	insuranceRequest.setInvoiceAmount(invoiceAmount);
	insuranceRequest.setInvoiceCurrency(invoiceCurrency);

	insuranceRequest.setInvoicePayeeName(invoicePayeeName);
	insuranceRequest.setInvoicePayeeEmail(invoicePayeeEmail);
	insuranceRequest.setInvoicePayeePhone(invoicePayeePhone);
	insuranceRequest.setInvoicePayeeTaxpayerNumber(invoicePayeeTaxpayerNumber);
	insuranceRequest.setInvoiceLanguage(invoiceLanguage);

	insuranceRequest.setInvoiceNumber(invoice.getNumber());

	final T ir1;
	try {
	    ir1 = dao.save(insuranceRequest);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return ir1;
    }

    private <T extends InsuranceRequest> void requireInStatus(T insuranceRequest, InsuranceRequestStatus... statuses) {
	if (insuranceRequest.insuranceRequestStatusIn(statuses))
	    return;
	throw MyExceptions.illegalStateFormat("Actual request status %1$s. Expected status in [%2$s]",
		insuranceRequest.few(), // 1
		Arrays.stream(statuses).map(it -> it.few()).collect(Collectors.joining(", ")) // 2
	);
    }

    private <T extends InsuranceRequest> T _policyIssued(T insuranceRequest, String agreementNumber)
	    throws IllegalArgumentException, IllegalStateException {

	MyObjects.requireNonNull(insuranceRequest, "insuranceRequest");
	MyStrings.requireNonEmpty(agreementNumber, "agreementNumber");

	requireInStatus(insuranceRequest, PENDING);

	insuranceRequest.setInsuranceRequestStatus(POLICY_ISSUED);
	insuranceRequest.setInsuranceRequestCancellationReason(null);
	insuranceRequest.setAgreementNumber(agreementNumber);

	final T ir1;
	try {
	    ir1 = dao.save(insuranceRequest);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e);
	}

	return ir1;
    }

    private final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(InsuranceRequestFacade.class) //
	    .build();

    @SuppressWarnings("unchecked")
    private <T extends InsuranceRequest> T _getById(Integer id) throws IllegalStateException, IllegalArgumentException {
	MyNumbers.requirePositive(id, "id");
	try {
	    final InsuranceRequest ir1 = dao.getById(id);
	    final T ir2 = (T) ir1;
	    return ir2;
	} catch (final NotFound e) {
	    throw MyExceptions.illegalStateFormat("Request not found with id %1$s", id);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}
    }
}
