package tech.lapsa.insurance.facade.beans;

import java.time.Instant;

import com.lapsa.insurance.domain.InsuranceRequest;
import com.lapsa.insurance.domain.PaymentData;
import com.lapsa.insurance.domain.Request;
import com.lapsa.insurance.elements.PaymentStatus;
import com.lapsa.insurance.elements.ProgressStatus;
import com.lapsa.insurance.elements.RequestStatus;

final class Requests {

    private Requests() {
    }

    static <T extends InsuranceRequest> T preSave(final T request) {
	preDates(request);
	preStatus(request);
	preProgressStatus(request);

	prePayment(request);
	return request;
    }

    // PRIVATE

    private static <T extends Request> T preDates(final T request) {
	if (request.getCreated() == null)
	    request.setCreated(Instant.now());
	request.setUpdated(Instant.now());
	return request;
    }

    private static <T extends Request> T preProgressStatus(final T request) {
	if (request.getProgressStatus() == null)
	    request.setProgressStatus(ProgressStatus.NEW);
	return request;
    }

    protected static <T extends Request> T preStatus(final T request) {
	if (request.getStatus() == null)
	    request.setStatus(RequestStatus.OPEN);
	return request;
    }

    private static <T extends InsuranceRequest> T prePayment(final T request) {
	if (request.getPayment() == null)
	    request.setPayment(new PaymentData());
	if (request.getPayment().getStatus() == null)
	    request.getPayment().setStatus(PaymentStatus.UNDEFINED);
	return request;
    }

}
