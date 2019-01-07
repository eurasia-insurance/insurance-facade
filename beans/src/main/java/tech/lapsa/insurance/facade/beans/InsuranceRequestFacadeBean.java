package tech.lapsa.insurance.facade.beans;

import java.time.Instant;
import java.util.Currency;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.InsuranceRequest;
import com.lapsa.insurance.domain.PaymentData;
import com.lapsa.insurance.domain.crm.User;
import com.lapsa.insurance.elements.InsuranceRequestCancellationReason;
import com.lapsa.insurance.elements.InsuranceRequestStatus;
import com.lapsa.insurance.elements.PaymentStatus;
import com.lapsa.insurance.elements.ProgressStatus;
import com.lapsa.international.localization.LocalizationLanguage;
import com.lapsa.international.phone.PhoneNumber;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.facade.EpaymentFacade.EpaymentFacadeRemote;
import tech.lapsa.epayment.facade.InvoiceNotFound;
import tech.lapsa.insurance.dao.InsuranceRequestDAO.InsuranceRequestDAORemote;
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
    public <T extends InsuranceRequest> T policyIssued(T insuranceRequest, User user, String agreementNumber)
	    throws IllegalArgument, IllegalState {
	try {
	    return _policyIssued(insuranceRequest, user, agreementNumber);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T policyIssuedAndInvoiceCreated(T insuranceRequest,
	    User user,
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
	    T ir1 = _policyIssued(insuranceRequest, user, agreementNumber);
	    T ir2 = _invoiceCreated(ir1,
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
	    User user,
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
	    T ir1 = _policyIssued(insuranceRequest, user, agreementNumber);
	    T ir2 = _premiumPaid(ir1,
		    paymentMethodName,
		    paymentInstant,
		    paymentAmount,
		    paymentCurrency,
		    paymentCard,
		    paymentCardBank,
		    paymentReference,
		    payerName);
	    return ir2;
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
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
	    String payerName) throws IllegalArgument {
	try {
	    return _premiumPaid(insuranceRequest,
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
    public <T extends InsuranceRequest> T requestCanceled(T insuranceRequest, User user,
	    InsuranceRequestCancellationReason insuranceRequestCancellationReason)
	    throws IllegalState, IllegalArgument {
	try {
	    return _requestCanceled(insuranceRequest, user, insuranceRequestCancellationReason);
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
	    insuranceRequest.setProgressStatus(ProgressStatus.NEW);

	if (insuranceRequest.getPayment() == null)
	    insuranceRequest.setPayment(new PaymentData());
	if (insuranceRequest.getPayment().getStatus() == null)
	    insuranceRequest.getPayment().setStatus(PaymentStatus.UNDEFINED);

	insuranceRequest.setInsuranceRequestStatus(InsuranceRequestStatus.PENDING);

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
	    User user,
	    InsuranceRequestCancellationReason insuranceRequestCancellationReason)
	    throws IllegalStateException, IllegalArgumentException {

	MyObjects.requireNonNull(insuranceRequest, "insuranceRequest");
	MyObjects.requireNonNull(user, "user");
	MyObjects.requireNonNull(insuranceRequestCancellationReason, "insuranceRequestCancellationReason");

	if (!InsuranceRequestStatus.PENDING.equals(insuranceRequest.getInsuranceRequestStatus()))
	    throw MyExceptions.illegalStateFormat("Request should have %1$s state to be changed to %2$s",
		    InsuranceRequestStatus.PENDING, InsuranceRequestStatus.REQUEST_CANCELED);

	insuranceRequest.setInsuranceRequestStatus(InsuranceRequestStatus.REQUEST_CANCELED);
	insuranceRequest.getPayment().setStatus(PaymentStatus.CANCELED);
	insuranceRequest.setInsuranceRequestCancellationReason(insuranceRequestCancellationReason);
	insuranceRequest.setAgreementNumber(null);

	final T ir1;
	try {
	    ir1 = dao.save(insuranceRequest);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e);
	}

	final String invoiceNumber = ir1.getPayment().getInvoiceNumber();
	if (MyStrings.nonEmpty(invoiceNumber))
	    try {
		epayments.expireInvoice(invoiceNumber);
	    } catch (IllegalArgument | IllegalState | InvoiceNotFound e) {
		// it should not happen
		throw new EJBException(e);
	    }

	return ir1;
    }

    private <T extends InsuranceRequest> T _premiumPaid(final T insuranceRequest,
	    final String paymentMethodName,
	    final Instant paymentInstant,
	    final Double paymentAmount,
	    final Currency paymentCurrency,
	    final String paymentCard,
	    final String paymentCardBank,
	    final String paymentReference,
	    final String payerName)
	    throws IllegalArgumentException {

	MyObjects.requireNonNull(insuranceRequest, "insuranceRequest");
	MyStrings.requireNonEmpty(paymentMethodName, "paymentMethodName");
	MyObjects.requireNonNull(paymentInstant, "paymentInstant");
	MyNumbers.requirePositive(paymentAmount, "paymentAmount");
	MyObjects.requireNonNull(paymentCurrency, "paymentCurrency");

	if (!InsuranceRequestStatus.POLICY_ISSUED.equals(insuranceRequest.getInsuranceRequestStatus()))
	    throw MyExceptions.illegalStateFormat("Request should have %1$s state to be changed to %2$s",
		    InsuranceRequestStatus.POLICY_ISSUED, InsuranceRequestStatus.PREMIUM_PAID);

	try {
	    insuranceRequest.setInsuranceRequestStatus(InsuranceRequestStatus.PREMIUM_PAID);

	    insuranceRequest.getPayment().setStatus(PaymentStatus.DONE);
	    insuranceRequest.getPayment().setMethodName(paymentMethodName);
	    insuranceRequest.getPayment().setAmount(paymentAmount);
	    insuranceRequest.getPayment().setCurrency(paymentCurrency);
	    insuranceRequest.getPayment().setCard(paymentCard);
	    insuranceRequest.getPayment().setCardBank(paymentCardBank);
	    insuranceRequest.getPayment().setReference(paymentReference);
	    insuranceRequest.getPayment().setInstant(paymentInstant);
	    insuranceRequest.getPayment().setPayerName(payerName);
	} catch (final NullPointerException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

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

	if (!InsuranceRequestStatus.POLICY_ISSUED.equals(insuranceRequest.getInsuranceRequestStatus()))
	throw MyExceptions.illegalStateFormat("Request should have %1$s state to create invoice",
		    InsuranceRequestStatus.POLICY_ISSUED);

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

	PaymentData p = insuranceRequest.getPayment();
	if (p == null) {
	    p = new PaymentData();
	    insuranceRequest.setPayment(p);
	}
	p.setStatus(PaymentStatus.PENDING);
	p.setInvoiceProductName(invoiceProductName);

	p.setInvoiceQuantity(invoiceQuantity);
	p.setInvoiceAmount(invoiceAmount);
	p.setInvoiceCurrency(invoiceCurrency);

	p.setInvoicePayeeName(invoicePayeeName);
	p.setInvoicePayeeEmail(invoicePayeeEmail);
	p.setInvoicePayeePhone(invoicePayeePhone);
	p.setInvoicePayeeTaxpayerNumber(invoicePayeeTaxpayerNumber);
	p.setInvoiceLanguage(invoiceLanguage);

	p.setInvoiceNumber(invoice.getNumber());

	final T ir1;
	try {
	    ir1 = dao.save(insuranceRequest);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return ir1;
    }

    private <T extends InsuranceRequest> T _policyIssued(T insuranceRequest, User user, String agreementNumber)
	    throws IllegalArgumentException, IllegalStateException {

	MyObjects.requireNonNull(insuranceRequest, "insuranceRequest");
	MyObjects.requireNonNull(user, "user");
	MyStrings.requireNonEmpty(agreementNumber, "agreementNumber");

	if (!InsuranceRequestStatus.PENDING.equals(insuranceRequest.getInsuranceRequestStatus()))
	    throw MyExceptions.illegalStateFormat("Request should have %1$s state to be changed to %2$s",
		    InsuranceRequestStatus.PENDING, InsuranceRequestStatus.POLICY_ISSUED);

	insuranceRequest.setInsuranceRequestStatus(InsuranceRequestStatus.POLICY_ISSUED);
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
	final T ir2;
	try {
	    InsuranceRequest ir1 = dao.getById(id);
	    ir2 = (T) ir1;
	    return ir2;
	} catch (final NotFound e) {
	    throw MyExceptions.illegalStateFormat("Request not found with id %1$s", id);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}
    }
}
