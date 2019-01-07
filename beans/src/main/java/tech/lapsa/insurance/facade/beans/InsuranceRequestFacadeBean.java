package tech.lapsa.insurance.facade.beans;

import java.time.Instant;
import java.util.Currency;
import java.util.Optional;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.CalculationData;
import com.lapsa.insurance.domain.InsuranceProduct;
import com.lapsa.insurance.domain.InsuranceRequest;
import com.lapsa.insurance.domain.PaymentData;
import com.lapsa.insurance.domain.RequesterData;
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
    public <T extends InsuranceRequest> T premiumPaid(T request,
	    String paymentMethodName,
	    Instant paymentInstant,
	    Double paymentAmount,
	    Currency paymentCurrency,
	    String paymentCard,
	    String paymentCardBank,
	    String paymentReference,
	    String payerName) throws IllegalArgument {
	try {
	    return _premiumPaid(request,
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
    public <T extends InsuranceRequest> T policyIssued(T request, User user, String agreementNumber)
	    throws IllegalState, IllegalArgument {
	try {
	    return _policyIssued(request, user, agreementNumber);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T policyIssuedAndPremiumPaid(T request,
	    User user,
	    String agreementNumber,
	    String paymentMethodName,
	    Double paymentAmount,
	    Currency paymentCurrency,
	    Instant paymentInstant,
	    String paymentReference,
	    String payerName) throws IllegalState, IllegalArgument {
	try {
	    return _policyIssuedAndPremiumPaid(request,
		    user,
		    agreementNumber,
		    paymentMethodName,
		    paymentAmount,
		    paymentCurrency,
		    paymentInstant,
		    paymentReference,
		    payerName);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T requestCanceled(T request, User user,
	    InsuranceRequestCancellationReason insuranceRequestCancellationReason)
	    throws IllegalState, IllegalArgument {
	try {
	    return _requestCanceled(request, user, insuranceRequestCancellationReason);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    private <T extends InsuranceRequest> T _requestCanceled(T request,
	    User user,
	    InsuranceRequestCancellationReason insuranceRequestCancellationReason)
	    throws IllegalStateException, IllegalArgumentException {

	MyObjects.requireNonNull(request, "request");
	MyObjects.requireNonNull(user, "user");
	MyObjects.requireNonNull(insuranceRequestCancellationReason, "insuranceRequestCancellationReason");

	if (request.getProgressStatus() == ProgressStatus.FINISHED)
	    throw MyExceptions.illegalStateFormat("Progress status is invalid %1$s", request.getProgressStatus());

	final Instant now = Instant.now();

	request.setCompleted(now);
	request.setCompletedBy(user);
	request.setProgressStatus(ProgressStatus.FINISHED);

	if (request.getPayment().getStatus() == PaymentStatus.DONE)
	    throw MyExceptions.illegalStateFormat("Request already paid");
	request.setInsuranceRequestStatus(InsuranceRequestStatus.REQUEST_CANCELED);
	request.getPayment().setStatus(PaymentStatus.CANCELED);
	request.setInsuranceRequestCancellationReason(insuranceRequestCancellationReason);
	request.setAgreementNumber(null);

	final T saved;
	try {
	    saved = dao.save(request);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e);
	}

	final String invoiceNumber = saved.getPayment().getInvoiceNumber();
	if (MyStrings.nonEmpty(invoiceNumber))
	    try {
		epayments.expireInvoice(invoiceNumber);
	    } catch (IllegalArgument | IllegalState | InvoiceNotFound e) {
		// it should not happen
		throw new EJBException(e);
	    }

	return saved;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T newRequest(final T request) throws IllegalArgument {
	try {
	    return _newRequest(request);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T newAcceptedRequest(final T request) throws IllegalArgument {
	try {
	    return _acceptRequest(_newRequest(request));
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public <T extends InsuranceRequest> T acceptRequest(T request,
	    String invoicePayeeName,
	    Currency invoiceCurrency,
	    LocalizationLanguage invoiceLanguage,
	    String invoicePayeeEmail,
	    PhoneNumber invoicePayeePhone,
	    TaxpayerNumber invoicePayeeTaxpayerNumber,
	    String invoiceProductName,
	    Double invoiceAmount,
	    Integer invoiceQuantity) throws IllegalArgument {
	try {
	    return _acceptRequest(request,
		    invoicePayeeName,
		    invoiceCurrency,
		    invoiceLanguage,
		    invoicePayeeEmail,
		    invoicePayeePhone,
		    invoicePayeeTaxpayerNumber,
		    invoiceProductName,
		    invoiceAmount,
		    invoiceQuantity);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    // PRIVATE

    @EJB
    private NotificationFacadeLocal notifications;

    @EJB
    private InsuranceRequestDAORemote dao;

    @Deprecated
    private <T extends InsuranceRequest> T _acceptRequest(final T request) throws IllegalArgumentException {

	setupPaymentOrder(request);

	final T ir;
	try {
	    ir = dao.save(request);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return ir;
    }

    private <T extends InsuranceRequest> T _acceptRequest(final T request,
	    String invoicePayeeName,
	    Currency invoiceCurrency,
	    LocalizationLanguage invoiceLanguage,
	    String invoicePayeeEmail,
	    PhoneNumber invoicePayeePhone,
	    TaxpayerNumber invoicePayeeTaxpayerNumber,
	    String invoiceProductName,
	    Double invoiceAmount,
	    Integer invoiceQuantity) throws IllegalArgumentException {

	setupPaymentOrder(request,
		invoicePayeeName,
		invoiceCurrency,
		invoiceLanguage,
		invoicePayeeEmail,
		invoicePayeePhone,
		invoicePayeeTaxpayerNumber,
		invoiceProductName,
		invoiceAmount,
		invoiceQuantity);

	final T ir;
	try {
	    ir = dao.save(request);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return ir;
    }

    private <T extends InsuranceRequest> T _newRequest(final T request) throws IllegalArgumentException {

	MyObjects.requireNonNull(request, "insuranceRequest");

	setupGeneral(request);

	final T ir;
	try {
	    ir = dao.save(request);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	setupNotifications(ir);

	try {
	    dao.save(ir);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	logger.INFO.log("New %4$s accepded from '%1$s' '<%2$s>' tel '%3$s' ", //
		ir.getRequester().getName(), // 1
		ir.getRequester().getEmail(), // 2
		ir.getRequester().getPhone(), // 3
		ir.getClass().getSimpleName() // 4
	);

	return ir;
    }

    private <T extends InsuranceRequest> T setupGeneral(final T request) {
	if (request.getCreated() == null)
	    request.setCreated(Instant.now());

	if (request.getProgressStatus() == null)
	    request.setProgressStatus(ProgressStatus.NEW);

	if (request.getPayment() == null)
	    request.setPayment(new PaymentData());
	if (request.getPayment().getStatus() == null)
	    request.getPayment().setStatus(PaymentStatus.UNDEFINED);

	return request;
    }

    private <T extends InsuranceRequest> T _premiumPaid(final T request,
	    final String paymentMethodName,
	    final Instant paymentInstant,
	    final Double paymentAmount,
	    final Currency paymentCurrency,
	    final String paymentCard,
	    final String paymentCardBank,
	    final String paymentReference,
	    final String payerName)
	    throws IllegalArgumentException {

	MyObjects.requireNonNull(request, "request");
	MyStrings.requireNonEmpty(paymentMethodName, "paymentMethodName");
	MyObjects.requireNonNull(paymentInstant, "paymentInstant");
	MyNumbers.requirePositive(paymentAmount, "paymentAmount");
	MyObjects.requireNonNull(paymentCurrency, "paymentCurrency");

	try {
	    request.getPayment().setStatus(PaymentStatus.DONE);
	    request.getPayment().setMethodName(paymentMethodName);
	    request.getPayment().setAmount(paymentAmount);
	    request.getPayment().setCurrency(paymentCurrency);
	    request.getPayment().setCard(paymentCard);
	    request.getPayment().setCardBank(paymentCardBank);
	    request.getPayment().setReference(paymentReference);
	    request.getPayment().setInstant(paymentInstant);
	    request.getPayment().setPayerName(payerName);
	} catch (final NullPointerException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	final T saved;
	try {
	    saved = dao.save(request);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	saved.unlazy();

	try {
	    notifications.send(Notification.builder() //
		    .withEvent(NotificationEventType.REQUEST_PAID) //
		    .withChannel(NotificationChannel.EMAIL) //
		    .forEntity(saved) //
		    .withRecipient(NotificationRecipientType.COMPANY) //
		    .build());
	} catch (final IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e.getMessage());
	}

	return saved;
    }

    @EJB
    private EpaymentFacadeRemote epayments;

    private <T extends InsuranceRequest> T setupPaymentOrder(final T request,
	    String invoicePayeeName,
	    Currency invoiceCurrency,
	    LocalizationLanguage invoiceLanguage,
	    String invoicePayeeEmail,
	    PhoneNumber invoicePayeePhone,
	    TaxpayerNumber invoicePayeeTaxpayerNumber,
	    String invoiceProductName,
	    Double invoiceAmount,
	    Integer invoiceQuantity)
	    throws IllegalArgumentException {
	MyObjects.requireNonNull(request, "request");
	MyObjects.requireNonNull(request.getId(), "request.id");
	final InvoiceBuilder builder = Invoice.builder() //
		.withGeneratedNumber() //
		.withConsumerName(MyStrings.requireNonEmpty(invoicePayeeName, "invoicePayeeName")) //
		.withCurrency(MyObjects.requireNonNull(invoiceCurrency, "invoiceCurrency")) //
		.withConsumerPreferLanguage(MyObjects.requireNonNull(invoiceLanguage, "invoiceLanguage"))
		//
		.withExternalId(request.getId()) //
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

	PaymentData p = request.getPayment();
	if (p == null) {
	    p = new PaymentData();
	    request.setPayment(p);
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

	return request;
    }

    @Deprecated
    private <T extends InsuranceRequest> T setupPaymentOrder(final T request) throws IllegalArgumentException {

	if (MyStrings.nonEmpty(request.getPayment().getInvoiceNumber()))
	    return request;

	final Optional<RequesterData> ord = MyOptionals.of(request.getRequester());

	final Optional<CalculationData> ocd = MyOptionals.of(request.getProduct()) //
		.map(InsuranceProduct::getCalculation);

	if (!ocd.isPresent())
	    return request; // for callback requests

	final Double invoiceAmount = ocd.map(CalculationData::getAmount) //
		.filter(MyNumbers::nonZero) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine an premium amount"));

	final Currency invoiceCurrency = ocd.map(CalculationData::getCurrency) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine an premium currency"));

	final LocalizationLanguage invoiceLanguage = ord.map(RequesterData::getPreferLanguage) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine the language"));

	final String invoiceProductName = MyOptionals.of(request.getProductType()) //
		.map(x -> x.regular(invoiceLanguage.getLocale())) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine an item name"));

	final Integer invoiceQuantity = 1;

	final String invoicePayeeName = ord.map(RequesterData::getName) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine a consumer name"));

	final String invoicePayeeEmail = ord.map(RequesterData::getEmail).orElse(null);
	final PhoneNumber invoicePayeePhone = ord.map(RequesterData::getPhone).orElse(null);
	final TaxpayerNumber invoucePayeeTaxpayerNumber = ord.map(RequesterData::getIdNumber).orElse(null);
	return setupPaymentOrder(request, invoicePayeeName, invoiceCurrency, invoiceLanguage, invoicePayeeEmail,
		invoicePayeePhone, invoucePayeeTaxpayerNumber, invoiceProductName, invoiceAmount, invoiceQuantity);
    }

    private <T extends InsuranceRequest> T setupNotifications(final T request) throws IllegalArgumentException {

	MyObjects.requireNonNull(request, "request");

	final NotificationBuilder builder = Notification.builder() //
		.withEvent(NotificationEventType.NEW_REQUEST) //
		.forEntity(request);

	switch (request.getType()) {
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
	    if (request.getRequester().getEmail() != null)
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
	return request;
    }

    private <T extends InsuranceRequest> T _policyIssued(T request, User user, String agreementNumber)
	    throws IllegalArgumentException, IllegalStateException {
	MyObjects.requireNonNull(request, "request");
	MyObjects.requireNonNull(user, "user");
	MyStrings.requireNonEmpty(agreementNumber, "agreementNumber");

	if (request.getProgressStatus() == ProgressStatus.FINISHED)
	    throw MyExceptions.illegalStateFormat("Progress status is invalid %1$s", request.getProgressStatus());

	final Instant now = Instant.now();

	request.setCompleted(now);
	request.setCompletedBy(user);
	request.setProgressStatus(ProgressStatus.FINISHED);

	final InsuranceRequest ir = MyObjects.requireA(request, InsuranceRequest.class);
	ir.setInsuranceRequestStatus(InsuranceRequestStatus.POLICY_ISSUED);
	ir.getPayment().setStatus(PaymentStatus.DONE);
	ir.setInsuranceRequestCancellationReason(null);
	ir.setAgreementNumber(agreementNumber);

	final T response;
	try {
	    response = dao.save(request);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e);
	}

	return response;
    }

    private <T extends InsuranceRequest> T _policyIssuedAndPremiumPaid(T request,
	    User user,
	    String agreementNumber,
	    String paymentMethodName,
	    Double paymentAmount,
	    Currency paymentCurrency,
	    Instant paymentInstant,
	    String paymentReference,
	    String payerName) throws IllegalArgumentException, IllegalStateException {
	T issued = _policyIssued(request, user, agreementNumber);
	T paid = _premiumPaid(issued,
		paymentMethodName,
		paymentInstant,
		paymentAmount,
		paymentCurrency,
		null,
		null,
		paymentReference,
		payerName);

	final String invoiceNumber = paid.getPayment().getInvoiceNumber();
	if (MyStrings.nonEmpty(invoiceNumber))
	    try {
		epayments.completeWithUnknownPayment(invoiceNumber,
			paymentAmount,
			paymentCurrency,
			paymentInstant,
			paymentReference,
			payerName);
	    } catch (IllegalArgument | IllegalState | InvoiceNotFound e) {
		// it should not happen
		throw new EJBException(e);
	    }
	return paid;
    }

    private final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(InsuranceRequestFacade.class) //
	    .build();

    @SuppressWarnings("unchecked")
    private <T extends InsuranceRequest> T _getById(Integer id) throws IllegalStateException, IllegalArgumentException {
	MyNumbers.requirePositive(id, "id");
	final T request;
	try {
	    InsuranceRequest ir = dao.getById(id);
	    request = (T) ir;
	    return request;
	} catch (final NotFound e) {
	    throw MyExceptions.illegalStateFormat("Request not found with id %1$s", id);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}
    }
}
