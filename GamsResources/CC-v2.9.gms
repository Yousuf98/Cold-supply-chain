$onEcho > howToRead.txt
dset=i     rng=i!A2      cDim=1
dset=subi  rng=subi!A2   cDim=1
dset=sub2i rng=sub2i!A2  cDim=1
dset=subj  rng=subj!A2   cDim=1
dset=sub2j rng=sub2j!A2  cDim=1
dset=k     rng=k!A2      cDim=1
dset=b     rng=b!A2      cDim=1
par=S      rng=S!A2      rDim=1  cDim=1
par=D      rng=D!A2      rDim=1  cDim=1
par=L      rng=L!A2      cDim=1
par=U      rng=U!A2      cDim=1
par=MSL    rng=MSL!A2    cDim=1
par=minSL  rng=minSL!A2  cDim=1
par=alpha  rng=alpha!A2  cDim=1
par=beta   rng=beta!A2   cDim=1
par=gamma  rng=gamma!A2  cDim=1
par=P      rng=P!A2      cDim=1
par=W      rng=W!A2      dim=0
par=M      rng=M!A2      dim=0
par=fm     rng=fm!A2     dim=0
par=fi     rng=fi!A2     dim=0
par=z      rng=z!A2      dim=0
par=R      rng=R!A2      cDim=1
par=EXP    rng=EXP!A2    dim=0
par=maxTemp       rng=maxTemp!A2       dim=0
par=minTemp       rng=minTemp!A2       dim=0
par=maxTempCost   rng=maxTempCost!A2   dim=0
par=costPerDegree rng=costPerDegree!A2 dim=0
par=TND      rng=K!A4   cDim=1
par=CLoad   rng=K!A13   cDim=1   rDim=1
par=Cap     rng=Cap!A2  cDim=1
par=TAD        rng=k!A7  cDim=1
par=delta        rng=k!A10  cDim=1
par=te           rng=te!A2     cDim=1
par=LS          rng=LS!A2   cDim=1  rDim=1
MaxDupeErrors = 100
$offEcho
$call gdxxrw CC-v2.9.xlsx @howToRead.txt
$gdxin CC-v2.9.gdx


Set i all locations;
alias(i,j);
set subi(i) First to Last retailer not including DCS or DCE;
set sub2i(i) starting warehouse and customers but not DCE;
set subj(j)  customers... same as subi;
set sub2j(j) customers and ending warehouse;
set k trucks;
set b products;


$load i subi sub2i subj sub2j k b
scalar N total number of nodes including warehouse start and warehouse end;
N=card(j);

scalar W fixed cost for using a truck: AED per truck;
scalar M large positive number;
scalar fm fuel cost for a moving truck: AED per hour;
scalar fi fuel cost for an idle truck: AED per hpur;
scalar z unloading speed: units per hour;
scalar EXP e;
scalars maxTemp,minTemp maximum and minimum temperature of trucks;
scalar maxTempCost refrigeration cost at max temp;
scalar costPerDegree refrigeration cost for lowering max temp by 1 degree;

$load W  M fm fi z EXP maxTemp minTemp maxTempCost costPerDegree

parameter S(i,j) ETA matrix;
parameter D(j,b), D(i,b) demand at location j of product b;
parameter L(j) lower limit of time window at customer j;
parameter U(j) upper limit of time window at customer j;
parameter MSL(b) maximum shelf life of product b: days;
parameter minSL(b) minimum acceptable shelf life;
parameters alpha(b), beta(b), gamma(b) coef. of door openings..transportation times..temperature;
parameter P(b) unit price of product b: AED per unit;
parameter Cap(k) Total capacity of each truck regardless of product;
parameter delta(k) Rank of the second-to-last customer served by truck k at the re-routing time. This parameter is 0 in the first (planning) run and non-zero in any re-routing run;
parameter te(k) Time elapsed since truck k left the DC. This parameter is 0 in the first (planning) run and non-zero in any re-routing run;
parameter R(j) penalty rate for being late;
Parameter LS(j,k) Last-served customer for each truck;
Parameter TND(k) Trucks not yet deployed;
Parameter CLoad(k,b) Current stock in trucks already deployed;
Parameter TAD(k) Trucks Already Deployed;

$load S D L U MSL minSL alpha beta gamma P Cap delta te R LS TND CLoad TAD
$gdxin


Variable Cost total cost;
Variable          tCost cost for using trucks;
Variable          dCost cost for distance traveled;
Variable          qCost cost for quality lost;
Variable          eCost cost for arriving early;
Variable          lCost cost for arriving late;
Variable          sCost quality cost of surplus;
Variable          surplus(k,b) amount of surplus in truck k of product b;
          


binary variables X(i,j,k) traveling from i to j using truck k
                 Y(k) using truck k
                 NTD(k) New Trucks Deployed
                 E(j) arriving early at location j
                 Q(j) arriving on time at location j
                 T(j) arriving late at location j;

positive variable AR(k,j) arrival time of truck k at location j;
AR.lo(k,j)=0;
positive variable RLoad(j,k,b), RLoad(i,k,b) The stock of product b in truck k at location j;
RLoad.lo(j,k,b)=0;
RLoad.up(j,k,b)=Cap(k);
integer variables rank(j,k),rank(i,k) subtour elimination constraint;
rank.lo(i,k)=0;
rank.up(j,k)=N+1;
integer variable temp(k) temperature of truck k;
positive variable SL(j,k,b) shelf life of product b at customer j;
positive variable rg(k) refrigeration cost of truck k;
positive variable C(i,j,k) cost of traveling from i to j using truck k;


Equations OF objective function
CostComp1,CostComp2,CostComp3,CostComp4,CostComp5,CostComp6,CostComp7,CostComp8,CostComp9
StkCon1,StkCon2,StkCon3,StkCon4
TrkCon1,TrkCon2
QualCon1,QualCon2,QualCon3,QualCon4,QualCon5
TransCon1,TransCon2,TransCon3,TransCon4,TransCon5,TransCon6,TransCon7,TransCon8,TransCon9,TransCon10,TransCon11,TransCon12,TransCon13
ArrivalCon1,ArrivalCon2,ArrivalCon3,ArrivalCon4,ArrivalCon5,ArrivalCon6,ArrivalCon7;


* OBJECTIVE FUNCTION
OF..Cost =e= tCost + dCost + qCost+ eCost + lCost+ sCost;

* Fixed Cost of Truck Hiring
CostComp1..    tCost =e= sum(k, W * Y(k));

*The cost of fuel and emissions while driving and while idling
CostComp2(i,j,k).. C(i,j,k) =e= (fm + rg(k)) * S(i,j)  + (fi + rg(k)) * sum(b, D(j,b))/z;
CostComp3..    dCost =e= sum((i,j,k), C(i,j,k) * X(i,j,k));

*Quality cost during transportation
CostComp4..    qCost =e= sum((i,j,k,b), P(b) *(MSL(b) - SL(j,k,b)) * D(j,b)/MSL(b) * X(i,j,k));


* Additional Quality Cost of Storing Surplus
CostComp5..    sCost=e=sum((k,b),surplus(k,b)*P(b)*(1-EXP**(-alpha(b)*(rank('DCE',k)+delta(k))-beta(b)*(12+AR(k,'DCE')-L('DCS')+te(k)+surplus(k,b)/z)-gamma(b)*temp(k))));


*Refrigeration cost per unit time
CostComp6(k)..     rg(k) =e= maxTempCost + (maxTemp - temp(k)) * costPerDegree;


**Penalties for missing retailer's time window
*Late Arrival Cost
CostComp7..    lCost =e= sum((sub2j(j),k), R(j) * T(j) * (sum(b, D(j,b))/z + AR(k,j)- U(j)));
*Early Arrival Cost
CostComp8..    eCost =e= sum((subj(j),k), fi * (L(j) - AR(k,j)) *  E(j));

*Definition of Surplus Amount as any positive stock returned to the warehouse
CostComp9(k,b).. surplus(k,b)=e=RLoad('DCE',k,b);

***********************************************************************************************************************

**Stock Constraints

*Setting the initial stock
StkCon1(k,b)$TND(k)..RLoad('DCS',k,b)=e=sum((sub2i(i),subj(j)),D(j,b)*X(i,j,k));
StkCon2(k,b)$TAD(k)..RLoad('DCS',k,b)=e=CLoad(k,b);


*Re-calculating stock upon leaving at each stop
StkCon3(sub2i(i),sub2j(j),k,b)..RLoad(i,k,b)=g=(RLoad(j,k,b)+D(i,b))*X(i,j,k);

*Proper Calculation of DCE Stock

StkCon4(k,b)$TAD(k)..RLoad('DCE',k,b)=e=RLoad('DCS',k,b)-sum((sub2i(i),subj(j)),D(j,b)*X(i,j,k));

**********************************************************************************************************************

* Truck Usage Constraints

*Trucks Used=Trucks Already Deployed+New Trucks Deployed
TrkCon1(k)..Y(k)=e=TAD(k)+NTD(k);

* Use trucks in order
TrkCon2(k)..    Y(k+1) =l= Y(k);

***********************************************************************************************************************

** Quality Constraints


*Shelf Life Equation
QualCon1(sub2j(j),k,b).. SL(j,k,b) =e= MSL(b)*EXP ** (-alpha(b) * (rank(j,k)+delta(k))-beta(b) * (AR(k,j)-L('DCS')+te(k)+D(j,b)/z)-gamma(b) * temp(k));
*Minimum Shelf Life Constraint
QualCon2(subj(j),k,b).. SL(j,k,b) =g= minSL(b);
*Maximum Shelf Life Constraint
QualCon3(k,b).. SL('DCS',k,b) =e= MSL(b);

* Truck Temperature Constraints
QualCon4(k).. temp(k)=g= minTemp;
QualCon5(k).. temp(k)=l= maxTemp;

*************************************************************************************************************************
** Transportation Constraints

* The total demand of a trip is less than the maximum load of the truck.
* (This is a redundant constraint in re-routing runs, but not in the initial planning run)

TransCon1(k)..sum((sub2i(i),subj(j),b),  D(j,b) * X(i,j,k)) =l= Cap(k) * Y(k);


*Each customer must be entered once by one truck

TransCon2(subj(j)).. sum((sub2i(i), k), X(i,j,k)) =e= 1;

*Each customer must be left once by one truck

TransCon3(subi(i)).. sum((sub2j(j), k), X(i,j,k)) =e= 1;

*The same truck has to make both the entry to, and exit from, each customer
TransCon4(subj(j),k).. sum(sub2i(i), X(i,j,k)) =e= sum(sub2j(i), X(j,i,k));

*The warehouse must be entered once and left once per truck
TransCon5(k).. sum(subj(j), X('DCS',j,k)) =e= Y(k);
TransCon6(k).. sum(subi(i), X(i,'DCE',k)) =e= Y(k);

*A trip cannot start from node N or end at node 0 or go directly from 0 to N.
TransCon7.. sum((i,k), X(i,'DCS',k)) =e= 0;
TransCon8.. sum((j,k), X('DCE',j,k)) =e= 0;
TransCon9.. sum(k, X('DCS','DCE',k)) =e= 0;

*Only for deployed trucks: The truck must go directly from DCS to its last-served customer

TransCon10(j,k)$LS(j,k)..X('DCS',j,k)=e=1;

* Subtour Elimination Constraints
TransCon11(sub2j(j),sub2i(i),k).. rank(j,k) =g= rank(i,k)+ 1 + M *( X(i,j,k) - 1);
TransCon12(k)..rank('DCE',k)=l=sum((sub2i(i),sub2j(j)),X(i,j,k));
TransCon13(k).. rank('DCS',k) =e= 0;
***********************************************************************************************************************

**Arrival Time Calculations

ArrivalCon1(sub2j(j),sub2i(i),k).. AR(k,j) =g= AR(k,i) + sum(b, D(i,b))/z + S(i,j) - (1 - X(i,j,k))* M;

ArrivalCon2(k).. AR(K,'DCS') =e= L('DCS')*Y(k);

* Setting values of Late/Early/On-Time Arrival Indicators

ArrivalCon3(k,sub2j(j)).. AR(k,j) =l= L(j)  +  M*(1- E(j)) ;
ArrivalCon4(k,sub2j(j)).. AR(k,j) =g= U(j)  -  M*(1- T(j)) ;
ArrivalCon5(k,sub2j(j)).. AR(k,j) =g= L(j)  -  M*(1- Q(j)) ;
ArrivalCon6(k,sub2j(j)).. AR(k,j) =l= U(j)  +  M*(1- Q(j)) ;
ArrivalCon7(sub2j(j)).. E(j) + Q(j) + T(j) =e= 1;



model simple /all/;
option MINLP=baron;
solve simple using MINLP minimizing Cost;


execute_unload 'CC-v2.9.gdx', D L U x S MSL SL Y surplus sCost E T Q AR Cost qCost tCost dCost lCost eCost rank RLoad Cap;

$onEcho > howToWrite.txt
text="Customers Demands:"        rng=Sheet1!A1
squeeze=n        par=D           rng=Sheet1!A2
text="Customers Time Windows:"   rng=Sheet1!A10
text="Lower limit:"              rng=Sheet1!A11
squeeze=n        par=L           rng=Sheet1!A12
text="Upper limit:"              rng=Sheet1!A14
squeeze=n        par=U           rng=Sheet1!A15
text="Solution Routes:"          rng=Sheet1!A18
var=x.l                          rng=Sheet1!A19
text="ETA Matrix:"               rng=Sheet2!A1
squeeze=n        par=S           rng=Sheet2!A2
text="Maximum Shelf Lives:"      rng=Sheet2!A13
squeeze=n        par=MSL         rng=Sheet2!A14
text="Shelf Lives:"              rng=Sheet3!A1
squeeze=n        var=SL          rng=Sheet3!A2
text="Surplus Cost:"             rng=Sheet4!A2
squeeze=n       var=sCost        rng=Sheet4!B2
text="Surplus:"                  rng=Sheet4!A4
squeeze=n        var=surplus     rng=Sheet4!A5
text="Early Arrival Indicators"  rng=Sheet5!A4
squeeze=n var=E                  rng=Sheet5!A5
text="Late Arrival Indicators"   rng=Sheet5!A7
squeeze=n var=T                  rng=Sheet5!A8
text="On-Time Arrival Indicators"   rng=Sheet5!A10
squeeze=n var=Q                  rng=Sheet5!A11
text="Arrival times by truck"     rng=Sheet5!A16
squeeze=n   var=AR.l             rng=Sheet5!A17
text="Total Cost"                  rng=Sheet6!A1
var=Cost                            rng=Sheet6!B1
text="Quality Cost"                  rng=Sheet6!A2
var=qCost                            rng=Sheet6!B2
text="Truck Hiring Cost"              rng=Sheet6!A3
var=tCost                               rng=Sheet6!B3
text="Transportation Cost"              rng=Sheet6!A4
var=dCost                               rng=Sheet6!B4
text="Late arrival Cost"                rng=Sheet6!A5
var=lCost                               rng=Sheet6!B5
text="Early arrival Cost"           rng=Sheet6!A6
var=eCost                           rng=Sheet6!B6
text="Rank"                         rng=Sheet6!A13
var=rank.l                            rng=Sheet6!A14
text="Trucks Used"                       rng=Sheet6!B8
var=Y.l                                    rng=Sheet6!B9
text="Current Stock"                rng=Sheet7!A1
var=RLoad.l                           rng=Sheet7!A2
$offEcho
execute 'gdxxrw CC-v2.9.gdx output=CC-v2.9-output.xlsx @howToWrite.txt';
