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
import com.lapsa.insurance.elements.PaymentStatus;
import com.lapsa.insurance.elements.ProgressStatus;
import com.lapsa.insurance.elements.RequestStatus;
import com.lapsa.international.localization.LocalizationLanguage;

import tech.lapsa.epayment.domain.Invoice;
import tech.lapsa.epayment.domain.Invoice.InvoiceBuilder;
import tech.lapsa.epayment.facade.EpaymentFacade.EpaymentFacadeRemote;
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
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyNumbers;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyOptionals;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.java.commons.logging.MyLogger;
import tech.lapsa.patterns.dao.NotFound;

@Stateless(name = InsuranceRequestFacade.BEAN_NAME)
public class InsuranceRequestFacadeBean implements InsuranceRequestFacadeLocal, InsuranceRequestFacadeRemote {

    // READERS

    // MODIFIERS

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
    public <T extends InsuranceRequest> T acceptRequest(T request) throws IllegalArgument {
	try {
	    return _acceptRequest(request);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }
    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void completePayment(final Integer id,
	    final String methodName,
	    final Instant paymentInstant,
	    final Double paymentAmount,
	    final Currency paymentCurrency,
	    final String paymentCard,
	    final String paymentCardBank,
	    final String paymentReference,
	    final String payerName)
	    throws IllegalArgument {
	try {
	    _completePayment(id,
		    methodName,
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

    // PRIVATE

    @EJB
    private NotificationFacadeLocal notifications;

    @EJB
    private InsuranceRequestDAORemote dao;

    private <T extends InsuranceRequest> T _acceptRequest(final T insuranceRequest) throws IllegalArgumentException {

	setupPaymentOrder(insuranceRequest);
	
	final T ir;
	try {
	    ir = dao.save(insuranceRequest);
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

	if (request.getStatus() == null)
	    request.setStatus(RequestStatus.OPEN);

	if (request.getProgressStatus() == null)
	    request.setProgressStatus(ProgressStatus.NEW);

	if (request.getPayment() == null)
	    request.setPayment(new PaymentData());
	if (request.getPayment().getStatus() == null)
	    request.getPayment().setStatus(PaymentStatus.UNDEFINED);

	return request;
    }

    private void _completePayment(final Integer id,
	    final String methodName,
	    final Instant paymentInstant,
	    final Double paymentAmount,
	    final Currency paymentCurrency,
	    final String paymentCard,
	    final String paymentCardBank,
	    final String paymentReference,
	    final String payerName)
	    throws IllegalArgumentException {

	MyNumbers.requirePositive(id, "id");
	MyStrings.requireNonEmpty(methodName, "methodName");
	MyObjects.requireNonNull(paymentInstant, "paymentInstant");
	MyNumbers.requirePositive(paymentAmount, "paymentAmount");
	MyObjects.requireNonNull(paymentCurrency, "paymentCurrency");

	final InsuranceRequest ir1;
	try {
	    ir1 = dao.getById(id);
	} catch (final NotFound e) {
	    throw MyExceptions.illegalArgumentFormat("Request not found with id %1$s", id);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	try {
	    ir1.getPayment().setStatus(PaymentStatus.DONE);
	    ir1.getPayment().setMethodName(methodName);
	    ir1.getPayment().setAmount(paymentAmount);
	    ir1.getPayment().setCurrency(paymentCurrency);
	    ir1.getPayment().setCard(paymentCard);
	    ir1.getPayment().setCardBank(paymentCardBank);
	    ir1.getPayment().setReference(paymentReference);
	    ir1.getPayment().setInstant(paymentInstant);
	    ir1.getPayment().setPayerName(payerName);
	} catch (final NullPointerException e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	final InsuranceRequest ir2;
	try {
	    ir2 = dao.save(ir1);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	ir2.unlazy();

	try {
	    notifications.send(Notification.builder() //
		    .withEvent(NotificationEventType.REQUEST_PAID) //
		    .withChannel(NotificationChannel.EMAIL) //
		    .forEntity(ir2) //
		    .withRecipient(NotificationRecipientType.COMPANY) //
		    .build());
	} catch (final IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e.getMessage());
	}
    }

    @EJB
    private EpaymentFacadeRemote epayments;

    private <T extends InsuranceRequest> T setupPaymentOrder(final T request) throws IllegalArgumentException {

	if (MyStrings.nonEmpty(request.getPayment().getInvoiceNumber()))
	    return request;

	final Optional<RequesterData> ord = MyOptionals.of(request.getRequester());

	final Optional<CalculationData> ocd = MyOptionals.of(request.getProduct()) //
		.map(InsuranceProduct::getCalculation);

	if (!ocd.isPresent())
	    return request; // for callback requests

	final LocalizationLanguage lang = ord.map(RequesterData::getPreferLanguage) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine the language"));

	final String itemName = MyOptionals.of(request.getProductType()) //
		.map(x -> x.regular(lang.getLocale())) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine an item name"));

	final Integer quantity = 1;

	final Double amount = ocd.map(CalculationData::getAmount) //
		.filter(MyNumbers::nonZero) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine an premium amount"));

	final String consumerName = ord.map(RequesterData::getName) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine a consumer name"));

	final Currency currency = ocd.map(CalculationData::getCurrency) //
		.orElseThrow(MyExceptions.illegalArgumentSupplier("Can't determine an premium currency"));

	final InvoiceBuilder builder = Invoice.builder() //
		.withGeneratedNumber() //
		.withConsumerName(consumerName) //
		.withCurrency(currency) //
		.withConsumerPreferLanguage(lang)
		//
		.withExternalId(request.getId()) //
		.withConsumerEmail(ord.map(RequesterData::getEmail)) //
		.withConsumerPhone(ord.map(RequesterData::getPhone)) //
		.withConsumerTaxpayerNumber(ord.map(RequesterData::getIdNumber)) //
		.withItem(itemName, quantity, amount);

	final Invoice invoice;
	try {
	    invoice = epayments.invoiceAccept(builder);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	request.getPayment().setInvoiceNumber(invoice.getNumber());
	request.getPayment().setStatus(PaymentStatus.PENDING);

	return request;
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

    private final MyLogger logger = MyLogger.newBuilder() //
	    .withNameOf(InsuranceRequestFacade.class) //
	    .build();

}
