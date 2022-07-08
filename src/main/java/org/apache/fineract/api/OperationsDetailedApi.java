package org.apache.fineract.api;

import org.apache.fineract.data.ErrorCode;
import org.apache.fineract.exception.WriteToCsvException;
import org.apache.fineract.operations.*;
import org.apache.fineract.utils.CsvUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import static org.apache.fineract.core.service.OperatorUtils.dateFormat;


@RestController
@RequestMapping("/api/v1")
public class OperationsDetailedApi {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private TransactionRequestRepository transactionRequestRepository;

    @GetMapping("/transfers")
    public Page<Transfer> transfers(
            @RequestParam(value = "page") Integer page,
            @RequestParam(value = "size") Integer size,
            @RequestParam(value = "payerPartyId", required = false) String payerPartyId,
            @RequestParam(value = "payerDfspId", required = false) String payerDfspId,
            @RequestParam(value = "payeePartyId", required = false) String payeePartyId,
            @RequestParam(value = "payeeDfspId", required = false) String payeeDfspId,
            @RequestParam(value = "transactionId", required = false) String transactionId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "amount", required = false) BigDecimal amount,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "startFrom", required = false) String startFrom,
            @RequestParam(value = "startTo", required = false) String startTo,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "sortedBy", required = false) String sortedBy,
            @RequestParam(value = "partyId", required = false) String partyId,
            @RequestParam(value = "partyIdType", required = false) String partyIdType,
            @RequestParam(value = "sortedOrder", required = false, defaultValue = "DESC") String sortedOrder) {
        List<Specifications<Transfer>> specs = new ArrayList<>();

        if (payerPartyId != null) {
            if (payerPartyId.contains("%2B")) {
                try {
                    payerPartyId = URLDecoder.decode(payerPartyId, "UTF-8");
                    logger.info("Decoded payerPartyId: " + payerPartyId);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            specs.add(TransferSpecs.match(Transfer_.payerPartyId, payerPartyId));
        }
        if (payeePartyId != null) {
            if (payeePartyId.contains("%2B")) {
                try {
                    payeePartyId = URLDecoder.decode(payeePartyId, "UTF-8");
                    logger.info("Decoded payeePartyId: " + payeePartyId);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            specs.add(TransferSpecs.match(Transfer_.payeePartyId, payeePartyId));
        }
        if (payeeDfspId != null) {
            specs.add(TransferSpecs.match(Transfer_.payeeDfspId, payeeDfspId));
        }
        if (payerDfspId != null) {
            specs.add(TransferSpecs.match(Transfer_.payerDfspId, payerDfspId));
        }
        if (transactionId != null) {
            specs.add(TransferSpecs.match(Transfer_.transactionId, transactionId));
        }
        if (status != null && parseStatus(status) != null) {
            specs.add(TransferSpecs.match(Transfer_.status, parseStatus(status)));
        }
        if (amount != null) {
            specs.add(TransferSpecs.match(Transfer_.amount, amount));
        }
        if (currency != null) {
            specs.add(TransferSpecs.match(Transfer_.currency, currency));
        }
        if (direction != null) {
            specs.add(TransferSpecs.match(Transfer_.direction, direction));
        }
        if (partyIdType != null) {
            specs.add(TransferSpecs.multiMatch(Transfer_.payeePartyIdType, Transfer_.payerPartyIdType, partyIdType));
        }
        if (partyId != null) {
            if (partyId.contains("%2B")) {
                try {
                    partyId = URLDecoder.decode(partyId, "UTF-8");
                    logger.info("Decoded PartyId: " + partyId);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            specs.add(TransferSpecs.multiMatch(Transfer_.payerPartyId, Transfer_.payeePartyId, partyId));
        }
        try {
            logger.info("StartTo: " + startTo);
            logger.info("StartFrom: " + startFrom);
            if (startFrom != null && startTo != null) {
                specs.add(TransferSpecs.between(Transfer_.startedAt, dateFormat().parse(startFrom), dateFormat().parse(startTo)));
            } else if (startFrom != null) {
                specs.add(TransferSpecs.later(Transfer_.startedAt, dateFormat().parse(startFrom)));
            } else if (startTo != null) {
                specs.add(TransferSpecs.earlier(Transfer_.startedAt, dateFormat().parse(startTo)));
            }
        } catch (Exception e) {
            logger.warn("failed to parse dates {} / {}", startFrom, startTo);
        }

        PageRequest pager;
        if (sortedBy == null || "startedAt".equals(sortedBy)) {
            pager = new PageRequest(page, size, new Sort(Sort.Direction.fromString(sortedOrder), "startedAt"));
        } else {
            pager = new PageRequest(page, size, new Sort(Sort.Direction.fromString(sortedOrder), sortedBy));
        }

        if (specs.size() > 0) {
            Specifications<Transfer> compiledSpecs = specs.get(0);
            for (int i = 1; i < specs.size(); i++) {
                compiledSpecs = compiledSpecs.and(specs.get(i));
            }

            return transferRepository.findAll(compiledSpecs, pager);
        } else {
            return transferRepository.findAll(pager);
        }
    }

    @GetMapping("/transactionRequests")
    public Page<TransactionRequest> transactionRequests(
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
            @RequestParam(value = "payerPartyId", required = false) String payerPartyId,
            @RequestParam(value = "payeePartyId", required = false) String payeePartyId,
            @RequestParam(value = "payeeDfspId", required = false) String payeeDfspId,
            @RequestParam(value = "payerDfspId", required = false) String payerDfspId,
            @RequestParam(value = "transactionId", required = false) String transactionId,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "amount", required = false) BigDecimal amount,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "startFrom", required = false) String startFrom,
            @RequestParam(value = "startTo", required = false) String startTo,
            @RequestParam(value = "direction", required = false) String direction,
            @RequestParam(value = "sortedBy", required = false) String sortedBy,
            @RequestParam(value = "sortedOrder", required = false, defaultValue = "DESC") String sortedOrder) {
        List<Specifications<TransactionRequest>> specs = new ArrayList<>();
        if (payerPartyId != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.payerPartyId, payerPartyId));
        }
        if (payeePartyId != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.payeePartyId, payeePartyId));
        }
        if (payeeDfspId != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.payeeDfspId, payeeDfspId));
        }
        if (payerDfspId != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.payerDfspId, payerDfspId));
        }
        if (transactionId != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.transactionId, transactionId));
        }
        if (state != null && parseState(state) != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.state, parseState(state)));
        }
        if (amount != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.amount, amount));
        }
        if (currency != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.currency, currency));
        }
        if (direction != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.direction, direction));
        }
        try {
            if (startFrom != null && startTo != null) {
                specs.add(TransactionRequestSpecs.between(TransactionRequest_.startedAt, dateFormat().parse(startFrom), dateFormat().parse(startTo)));
            } else if (startFrom != null) {
                specs.add(TransactionRequestSpecs.later(TransactionRequest_.startedAt, dateFormat().parse(startFrom)));
            } else if (startTo != null) {
                specs.add(TransactionRequestSpecs.earlier(TransactionRequest_.startedAt, dateFormat().parse(startTo)));
            }
        } catch (Exception e) {
            logger.warn("failed to parse dates {} / {}", startFrom, startTo);
        }

        PageRequest pager;
        if (sortedBy == null || "startedAt".equals(sortedBy)) {
            pager = new PageRequest(page, size, new Sort(Sort.Direction.valueOf(sortedOrder), "startedAt"));
        } else {
            pager = new PageRequest(page, size, new Sort(Sort.Direction.valueOf(sortedOrder), sortedBy));
        }

        if (specs.size() > 0) {
            Specifications<TransactionRequest> compiledSpecs = specs.get(0);
            for (int i = 1; i < specs.size(); i++) {
                compiledSpecs = compiledSpecs.and(specs.get(i));
            }

            return transactionRequestRepository.findAll(compiledSpecs, pager);
        } else {
            return transactionRequestRepository.findAll(pager);
        }
    }

    /**
     * Filter the [TransactionRequests] based on multiple type of ids
     * @param response instance of HttpServletResponse
     * @param page the count/number of page which we want to fetch
     * @param size the size of the single page defaults to [10000]
     * @param sortedOrder the order of sorting [ASC] or [DESC], defaults to [DESC]
     */
    @PostMapping("/transactionRequests/export")
    public Map<String, String> filterTransactionRequests(
            HttpServletResponse response,
            @RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
            @RequestParam(value = "size", required = false, defaultValue = "10000") Integer size,
            @RequestParam(value = "sortedOrder", required = false, defaultValue = "DESC") String sortedOrder,
            @RequestParam(value = "startFrom", required = false) String startFrom,
            @RequestParam(value = "startTo", required = false) String startTo,
            @RequestParam(value = "state", required = false) String state,
            @RequestBody Map<String, List<String>> body) {

        List<String> filterByList = new ArrayList<>(body.keySet());

        List<Specifications<TransactionRequest>> specs = new ArrayList<>();
        if (state != null && parseState(state) != null) {
            specs.add(TransactionRequestSpecs.match(TransactionRequest_.state, parseState(state)));
        }
        try {
            if (startFrom != null && startTo != null) {
                specs.add(TransactionRequestSpecs.between(TransactionRequest_.startedAt, dateFormat().parse(startFrom), dateFormat().parse(startTo)));
            } else if (startFrom != null) {
                specs.add(TransactionRequestSpecs.later(TransactionRequest_.startedAt, dateFormat().parse(startFrom)));
            } else if (startTo != null) {
                specs.add(TransactionRequestSpecs.earlier(TransactionRequest_.startedAt, dateFormat().parse(startTo)));
            }
        } catch (Exception e) {
            logger.warn("failed to parse dates {} / {}", startFrom, startTo);
        }

        Specifications<TransactionRequest> spec = null;

        List<TransactionRequest> data = new ArrayList<>();
        for (String filterBy : filterByList) {
            List<String> ids = body.get(filterBy);
            if (ids.isEmpty()) { continue; }
            Filter filter;
            try {
                filter = parseFilter(filterBy);
                logger.info("Filter parsed successfully " + filter.name());
            } catch (Exception e) {
                Map<String, String> res = new HashMap<>();
                res.put("errorCode", ErrorCode.INVALID_FILTER.name());
                res.put("errorDescription", "Invalid filter value " + filterBy);
                res.put("developerMessage", "Possible filter values are " + EnumSet.allOf(Filter.class));
                logger.info("Unable to parse filter " + filterBy);
                logger.info(res.toString());
                continue;
            }

            switch (filter) {
                case TRANSACTIONID:
                    spec = TransactionRequestSpecs.in(TransactionRequest_.transactionId, ids);
                    break;
                case PAYERID:
                    spec = TransactionRequestSpecs.in(TransactionRequest_.payerPartyId, ids);
                    break;
                case PAYEEID:
                    spec = TransactionRequestSpecs.in(TransactionRequest_.payeePartyId, ids);
                    break;
                case WORKFLOWINSTANCEKEY:
                    spec = TransactionRequestSpecs.in(TransactionRequest_.workflowInstanceKey, ids);
                    break;
                case STATE:
                    spec = TransactionRequestSpecs.in(TransactionRequest_.state, parseStates(ids));
                    break;
                case ERRORDESCRIPTION:
                    spec = TransactionRequestSpecs.filterByErrorDescription(parseErrorDescription(ids));
                    break;
                case EXTERNALID:
                    spec = TransactionRequestSpecs.in(TransactionRequest_.externalId, ids);
                    break;
            }

            PageRequest pager = new PageRequest(page, size, new Sort(Sort.Direction.valueOf(sortedOrder), "startedAt"));
            Page<TransactionRequest> result;
            if (spec == null) {
                result = transactionRequestRepository.findAll(pager);
            } else {
                for (int i = 0; i < specs.size(); i++) {
                    spec = spec.and(specs.get(i));
                }
                result = transactionRequestRepository.findAll(spec, pager);
            }
            data.addAll(result.getContent());

            logger.info("Result for " + filter + " : " + data);
        }
        if(data.isEmpty()) {
            Map<String, String> res = new HashMap<>();
            res.put("errorCode", "404");
            res.put("errorDescription", "Empty response");
            res.put("developerMessage", "Empty response");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return  res;
        }
        try {
            CsvUtility.writeToCsv(response, data);
        } catch (WriteToCsvException e) {
            Map<String, String> res = new HashMap<>();
            res.put("errorCode", e.getErrorCode());
            res.put("errorDescription", e.getErrorDescription());
            res.put("developerMessage", e.getDeveloperMessage());
            return  res;
        }
        return null;
    }

    /*
     * Generates the exhaustive errorDescription list by prefixing and suffixing it with double quotes (")
     *
     * Example: [ "AMS Local is disabled"] => [ "AMS Local is disabled", "\"AMS Local is disabled\""]
     */
    private List<String> parseErrorDescription(List<String> description) {
        List<String> errorDesc = new ArrayList<>(description);
        for (String s: description) {
            errorDesc.add(String.format("\"%s\"", s));
        }
        return errorDesc;
    }

    /*
     * Parses the [Filter] enum from filter string
     */
    private Filter parseFilter(String filterBy) {
        return filterBy == null ? null : Filter.valueOf(filterBy.toUpperCase());
    }

    /*
     * Parses the [TransferStatus] enum from transactionStatus string
     */
    private TransferStatus parseStatus(@RequestParam(value = "transactionStatus", required = false) String
                                               transactionStatus) {
        try {
            return transactionStatus == null ? null : TransferStatus.valueOf(transactionStatus);
        } catch (Exception e) {
            logger.warn("failed to parse transaction status {}, ignoring it", transactionStatus);
            return null;
        }
    }

    /*
     * Parses the [TransactionRequestState] enum from transactionState string
     */
    private TransactionRequestState parseState(String state) {
        try {
            return state == null ? null : TransactionRequestState.valueOf(state);
        } catch (Exception e) {
            logger.warn("failed to parse TransactionRequestState {}, ignoring it", state);
            return null;
        }
    }

    /*
     * Parses the list of [TransactionRequestState] enum from list of transactionState string
     */
    private List<TransactionRequestState> parseStates(List<String> states) {
        List<TransactionRequestState> stateList = new ArrayList<>();
        for(String state: states) {
            stateList.add(parseState(state));
        }
        return stateList;
    }
}
