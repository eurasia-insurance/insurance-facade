package tech.lapsa.insurance.facade.beans;

import java.time.Instant;
import java.util.Currency;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.InsuranceRequest;
import com.lapsa.insurance.domain.Request;
import com.lapsa.insurance.domain.crm.User;
import com.lapsa.insurance.elements.PaymentStatus;
import com.lapsa.insurance.elements.ProgressStatus;
import com.lapsa.insurance.elements.TransactionProblem;
import com.lapsa.insurance.elements.TransactionStatus;

import tech.lapsa.epayment.facade.EpaymentFacade.EpaymentFacadeRemote;
import tech.lapsa.epayment.facade.InvoiceNotFound;
import tech.lapsa.insurance.dao.RequestDAO.RequestDAORemote;
import tech.lapsa.insurance.facade.InsuranceRequestFacade.InsuranceRequestFacadeLocal;
import tech.lapsa.insurance.facade.RequestCompletionFacade;
import tech.lapsa.insurance.facade.RequestCompletionFacade.RequestCompletionFacadeLocal;
import tech.lapsa.insurance.facade.RequestCompletionFacade.RequestCompletionFacadeRemote;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.exceptions.IllegalState;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyNumbers;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyStrings;

@Stateless(name = RequestCompletionFacade.BEAN_NAME)
public class RequestCompletionFacadeBean
	implements RequestCompletionFacadeLocal, RequestCompletionFacadeRemote {

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Request transactionCompleteWithPayment(final Request request,
	    final User user,
	    final String note,
	    final String agreementNumber,
	    final String paymentMethodName,
	    final Double paymentAmount,
	    final Currency paymentCurrency,
	    final Instant paymentInstant,
	    final String paymentReference)
	    throws IllegalState, IllegalArgument {
	try {
	    return _transactionCompleteWithPayment(request, user, note, agreementNumber, paymentMethodName, paymentAmount, paymentCurrency,
		    paymentInstant, paymentReference);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Request transactionComplete(final Request request,
	    final User user,
	    final String note,
	    final String agreementNumber)
	    throws IllegalState, IllegalArgument {
	try {
	    return _transactionComplete(request, user, note, agreementNumber);
	} catch (IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	} catch (IllegalStateException e) {
	    throw new IllegalState(e);
	}
    }

    @Override
    public Request transactionUncomplete(final Request request,
	    final User user,
	    final String note,
	    final TransactionProblem transactionProblem,
	    final boolean paidable) throws IllegalState, IllegalArgument {
	return _transactionUncomplete(request, user, note, transactionProblem, paidable);
    }

    // EJBs

    // insurance-dao (remote)

    @EJB
    private RequestDAORemote requestDAO;

    // epayment-facade (remote)

    @EJB
    private EpaymentFacadeRemote epayments;

    // epayment-facade (local)

    @EJB
    private InsuranceRequestFacadeLocal insuranceRequests;

    private Request _transactionComplete(final Request request, final User user, final String note,
	    final String agreementNumber) throws IllegalArgumentException, IllegalStateException {

	MyObjects.requireNonNull(request, "request");
	MyObjects.requireNonNull(user, "user");
	MyStrings.requireNonEmpty(agreementNumber, "agreementNumber");

	if (request.getProgressStatus() == ProgressStatus.FINISHED)
	    throw MyExceptions.illegalStateFormat("Progress status is invalid %1$s", request.getProgressStatus());

	final Instant now = Instant.now();

	request.setUpdated(now);
	request.setCompleted(now);
	request.setCompletedBy(user);
	request.setNote(note);
	request.setProgressStatus(ProgressStatus.FINISHED);

	if (MyObjects.isA(request, InsuranceRequest.class)) {
	    final InsuranceRequest ir = MyObjects.requireA(request, InsuranceRequest.class);
	    ir.setTransactionStatus(TransactionStatus.COMPLETED);
	    ir.getPayment().setStatus(PaymentStatus.DONE);
	    ir.setTransactionProblem(null);
	    ir.setAgreementNumber(agreementNumber);
	}

	final Request response;
	try {
	    response = requestDAO.save(request);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e);
	}

	return response;
    }

    private Request _transactionCompleteWithPayment(final Request request,
	    final User user,
	    final String note,
	    final String agreementNumber,
	    final String paymentMethodName,
	    final Double paymentAmount,
	    final Currency paymentCurrency,
	    final Instant paymentInstant,
	    final String paymentReference) throws IllegalArgumentException, IllegalStateException {

	MyNumbers.requirePositive(paymentAmount, "paymentAmount");
	MyStrings.requireNonEmpty(paymentMethodName,"paymentMethodName");
	MyObjects.requireNonNull(paymentCurrency, "paymentCurrency");
	MyObjects.requireNonNull(paymentInstant, "paymentInstant");

	final Request response = _transactionComplete(request, user, note, agreementNumber);

	if (MyObjects.isA(request, InsuranceRequest.class)) {
	    final InsuranceRequest ir = MyObjects.requireA(response, InsuranceRequest.class);

	    try {
		insuranceRequests.completePayment(ir.getId(),
			paymentMethodName,
			paymentInstant,
			paymentAmount,
			paymentCurrency,
			paymentReference);
	    } catch (IllegalArgument e) {
		// it should not happen
		throw new EJBException(e);
	    }

	    final String invoiceNumber = ir.getPayment().getInvoiceNumber();
	    if (MyStrings.nonEmpty(invoiceNumber))
		try {
		    epayments.completeWithUnknownPayment(invoiceNumber,
			    paymentAmount,
			    paymentCurrency,
			    paymentInstant,
			    paymentReference);
		} catch (IllegalArgument | IllegalState | InvoiceNotFound e) {
		    // it should not happen
		    throw new EJBException(e);
		}
	}

	return response;
    }

    private Request _transactionUncomplete(final Request request,
	    final User user,
	    final String note,
	    final TransactionProblem transactionProblem,
	    final boolean paidable) throws IllegalStateException, IllegalArgumentException {

	MyObjects.requireNonNull(request, "request");
	MyObjects.requireNonNull(user, "user");
	MyObjects.requireNonNull(transactionProblem, "transactionProblem");

	if (request.getProgressStatus() == ProgressStatus.FINISHED)
	    throw MyExceptions.illegalStateFormat("Progress status is invalid %1$s", request.getProgressStatus());

	final Instant now = Instant.now();

	request.setUpdated(now);
	request.setCompleted(now);
	request.setCompletedBy(user);
	request.setNote(note);
	request.setProgressStatus(ProgressStatus.FINISHED);

	if (MyObjects.isA(request, InsuranceRequest.class)) {
	    final InsuranceRequest ir = MyObjects.requireA(request, InsuranceRequest.class);
	    if (ir.getPayment().getStatus() == PaymentStatus.DONE)
		throw MyExceptions.illegalStateFormat("Request already paid");
	    ir.setTransactionStatus(TransactionStatus.NOT_COMPLETED);
	    ir.getPayment().setStatus(PaymentStatus.CANCELED);
	    ir.setTransactionProblem(transactionProblem);
	    ir.setAgreementNumber(null);
	}

	final Request response;
	try {
	    response = requestDAO.save(request);
	} catch (IllegalArgument e) {
	    // it should not happen
	    throw new EJBException(e);
	}

	if (paidable)
	    if (MyObjects.isA(request, InsuranceRequest.class)) {
		final InsuranceRequest ir = MyObjects.requireA(response, InsuranceRequest.class);
		final String invoiceNumber = ir.getPayment().getInvoiceNumber();
		if (MyStrings.nonEmpty(invoiceNumber))
		    try {
			epayments.expireInvoice(invoiceNumber);
		    } catch (IllegalArgument | IllegalState | InvoiceNotFound e) {
			// it should not happen
			throw new EJBException(e);
		    }
	    }

	return response;
    }

}
