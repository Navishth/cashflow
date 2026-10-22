package com.cashflow.finance;

import com.cashflow.auth.AuthService;
import com.cashflow.common.ApiException;
import com.cashflow.data.Models.*;
import com.cashflow.data.Repositories.*;
import com.cashflow.finance.FinanceDtos.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.cashflow.common.ApiException.require;

@Service @Transactional
public class FinanceService {
    private final AuthService auth; private final Businesses businesses;
    private final Flows flows; private final Rules rules;
    public FinanceService(AuthService auth,Businesses businesses,Flows flows,Rules rules) { this.auth=auth; this.businesses=businesses; this.flows=flows; this.rules=rules; }
    public Business current() { return businesses.findByOwnerId(auth.userId()).orElseThrow(ApiException::notFound); }
    public LocalDate today(Business b) { return LocalDate.now(ZoneId.of(b.timezone)); }
    public void touch(Business b) { b.updatedAt=Instant.now(); businesses.saveAndFlush(b); }
    public static BigDecimal signed(FlowType type,BigDecimal amount) { return type==FlowType.INFLOW?amount:amount.negate(); }
    public static boolean settled(Flow f) { return f.status==FlowStatus.PAID || f.status==FlowStatus.RECEIVED; }
    public BigDecimal balance(Business b,List<Flow> entries) {
        return entries.stream().filter(FinanceService::settled).filter(f->!f.expectedDate.isBefore(b.openingDate)&&!f.expectedDate.isAfter(today(b)))
            .map(f->signed(f.type,f.amount)).reduce(b.openingBalance,BigDecimal::add);
    }
    public BusinessView businessView(Business b) { return new BusinessView(b.id,b.name,b.currency,b.timezone,b.openingDate,b.openingBalance,b.reserve,b.demo,balance(b,flows.findByBusinessId(b.id)),today(b),b.revision); }
    private void checkBusiness(BusinessInput d) {
        require("INR".equals(d.currency()),"Only INR is supported.");
        try { ZoneId.of(d.timezone()); } catch(Exception e) { throw new ApiException(400,"Choose a valid IANA timezone."); }
        require(!d.openingDate().isBefore(LocalDate.of(2000,1,1)) && !d.openingDate().isAfter(LocalDate.now(ZoneId.of(d.timezone()))),"Opening date must be between 2000 and today.");
    }
    public BusinessView createBusiness(BusinessInput d) {
        checkBusiness(d);
        if(businesses.findByOwnerId(auth.userId()).isPresent()) throw new ApiException(409,"You already have a business.");
        var b=new Business(); b.ownerId=auth.userId(); apply(b,d); b.demo=d.demo();
        if(b.demo) { b.openingDate=today(b).minusDays(120); b.openingBalance=new BigDecimal("180000"); }
        businesses.saveAndFlush(b);
        if(b.demo) seed(b);
        return businessView(b);
    }
    private void apply(Business b,BusinessInput d) { b.name=d.name().trim(); b.currency=d.currency(); b.timezone=d.timezone(); b.openingDate=d.openingDate(); b.openingBalance=d.openingBalance(); b.reserve=d.reserve(); }
    public BusinessView updateBusiness(BusinessInput d) {
        checkBusiness(d); var b=current(); require(d.demo()==b.demo,"The demo label cannot be changed.");
        require(flows.findByBusinessId(b.id).stream().noneMatch(f->f.expectedDate.isBefore(d.openingDate())),"Opening date cannot exclude existing records.");
        apply(b,d); touch(b); return businessView(b);
    }
    public PageView<FlowView> listFlows(LocalDate from,LocalDate to,FlowType type,String category,FlowStatus status,int page,int size) {
        require(page>=0 && size>=1 && size<=100,"Invalid page or size.");
        require(from==null||to==null||!from.isAfter(to),"From date must not follow to date.");
        UUID businessId=current().id;
        Specification<Flow> spec=(root,q,cb)->{
            var p=new ArrayList<jakarta.persistence.criteria.Predicate>(); p.add(cb.equal(root.get("businessId"),businessId));
            if(from!=null) p.add(cb.greaterThanOrEqualTo(root.get("expectedDate"),from));
            if(to!=null) p.add(cb.lessThanOrEqualTo(root.get("expectedDate"),to));
            if(type!=null) p.add(cb.equal(root.get("type"),type));
            if(status!=null) p.add(cb.equal(root.get("status"),status));
            if(category!=null&&!category.isBlank()) p.add(cb.equal(root.get("category"),category));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PageView.of(flows.findAll(spec,PageRequest.of(page,size,Sort.by(Sort.Order.desc("expectedDate"),Sort.Order.asc("id")))).map(FlowView::of));
    }
    public FlowView getFlow(UUID id) { return FlowView.of(flows.findByIdAndBusinessId(id,current().id).orElseThrow(ApiException::notFound)); }
    public FlowView saveFlow(UUID id,FlowInput d) {
        var b=current(); var f=id==null?new Flow():flows.findByIdAndBusinessId(id,b.id).orElseThrow(ApiException::notFound);
        require(!d.expectedDate().isBefore(b.openingDate) && !d.expectedDate().isAfter(today(b).plusYears(5)),"Date must follow opening date and be within five years.");
        require(d.status()!=FlowStatus.RECEIVED || d.type()==FlowType.INFLOW,"Received status requires an inflow.");
        require(d.status()!=FlowStatus.PAID || d.type()==FlowType.OUTFLOW,"Paid status requires an outflow.");
        boolean actual=d.status()==FlowStatus.RECEIVED||d.status()==FlowStatus.PAID;
        require(!actual||!d.expectedDate().isAfter(today(b)),"A settled transaction cannot have a future date.");
        require(d.source()!=Source.UNPLANNED||actual,"Unplanned movements must already be settled.");
        require((d.recurringRuleId()==null)==(d.occurrenceDate()==null),"A recurring entry requires both rule and occurrence date.");
        if(d.recurringRuleId()!=null) {
            var rule=rules.findByIdAndBusinessId(d.recurringRuleId(),b.id).orElseThrow(ApiException::notFound);
            require(Recurrence.isOccurrence(rule,d.occurrenceDate()),"Date is not an occurrence of this rule.");
            require(d.source()==Source.SCHEDULED,"Recurring entries must be scheduled.");
            require(d.type()==rule.type,"Recurring entry direction must match its rule.");
        }
        f.businessId=b.id; f.type=d.type(); f.amount=d.amount(); f.description=d.description().trim(); f.category=d.category().trim();
        f.counterparty=d.counterparty(); f.expectedDate=d.expectedDate(); f.status=d.status(); f.source=d.source();
        f.recurringRuleId=d.recurringRuleId(); f.occurrenceDate=d.occurrenceDate(); flows.saveAndFlush(f); touch(b); return FlowView.of(f);
    }
    public void deleteFlow(UUID id) { var b=current(); flows.delete(flows.findByIdAndBusinessId(id,b.id).orElseThrow(ApiException::notFound)); touch(b); }
    public List<RuleView> listRules() { return rules.findByBusinessIdOrderByStartDate(current().id).stream().map(RuleView::of).toList(); }
    public RuleView saveRule(UUID id,RuleInput d) {
        var b=current(); var r=id==null?new Rule():rules.findByIdAndBusinessId(id,b.id).orElseThrow(ApiException::notFound);
        require(!d.startDate().isBefore(b.openingDate)&&!d.startDate().isAfter(today(b).plusYears(5)),"Rule start date is outside the supported range.");
        require(d.endDate()==null||(!d.endDate().isBefore(d.startDate())&&!d.endDate().isAfter(today(b).plusYears(10))),"Invalid rule end date.");
        if(id!=null && flows.existsByRecurringRuleId(id)) require(r.startDate.equals(d.startDate())&&r.frequency==d.frequency()&&r.type==d.type(),"A rule with recorded occurrences cannot change its anchor, frequency, or direction. Deactivate it and create a new rule.");
        r.businessId=b.id; r.type=d.type(); r.amount=d.amount(); r.description=d.description().trim(); r.category=d.category().trim(); r.frequency=d.frequency();
        r.startDate=d.startDate(); r.endDate=d.endDate(); r.active=d.active(); rules.saveAndFlush(r); touch(b); return RuleView.of(r);
    }
    public void deleteRule(UUID id) { var b=current(); var r=rules.findByIdAndBusinessId(id,b.id).orElseThrow(ApiException::notFound); r.active=false; touch(b); }
    private void seed(Business b) {
        var random=new Random(42); LocalDate today=today(b);
        for(int n=0;n<=120;n++) {
            LocalDate date=b.openingDate.plusDays(n);
            var f=new Flow(); f.businessId=b.id; f.type=FlowType.INFLOW; f.amount=BigDecimal.valueOf(450+date.getDayOfWeek().getValue()*95+random.nextInt(350));
            f.description="Sample daily sales"; f.category="Sales"; f.expectedDate=date; f.status=FlowStatus.RECEIVED; f.source=Source.UNPLANNED; flows.save(f);
            var e=new Flow(); e.businessId=b.id; e.type=FlowType.OUTFLOW; e.amount=BigDecimal.valueOf(350+random.nextInt(350)); e.description="Sample operating cost"; e.category="Operations"; e.expectedDate=date; e.status=FlowStatus.PAID; e.source=Source.UNPLANNED; flows.save(e);
        }
        int[] offsets={3,7,12,16,21,26}; String[] labels={"Supplier invoice","Studio rent","Client milestone","Equipment payment","Client retainer","Contractor payment"};
        String[] amounts={"42000","28000","125000","78000","65000","40000"};
        for(int i=0;i<offsets.length;i++) {
            var f=new Flow(); f.businessId=b.id; f.type=(i==2||i==4)?FlowType.INFLOW:FlowType.OUTFLOW; f.amount=new BigDecimal(amounts[i]); f.description=labels[i]; f.category=f.type==FlowType.INFLOW?"Client payments":"Operations"; f.expectedDate=today.plusDays(offsets[i]); f.status=FlowStatus.PLANNED; f.source=Source.SCHEDULED; flows.save(f);
        }
        var r=new Rule(); r.businessId=b.id; r.type=FlowType.OUTFLOW; r.amount=new BigDecimal("3500"); r.description="Weekly software & logistics"; r.category="Operations"; r.frequency=Frequency.WEEKLY; r.startDate=today.plusDays(2); r.active=true; rules.save(r);
    }
}
