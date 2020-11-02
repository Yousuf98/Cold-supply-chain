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
par=RLoad     rng=RLoad!A2     rDim=1 cDim=1
par=M      rng=M!A2      dim=0
par=fm     rng=fm!A2     dim=0
par=fi     rng=fi!A2     dim=0
par=z      rng=z!A2      dim=0
par=R      rng=R!A2      dim=0
par=EXP    rng=EXP!A2    dim=0
par=maxTemp       rng=maxTemp!A2       dim=0
par=minTemp       rng=minTemp!A2       dim=0
par=maxTempCost   rng=maxTempCost!A2   dim=0
par=costPerDegree rng=costPerDegree!A2 dim=0
par=Ys      rng=Ys!A1   dim=0
par=Cap     rng=Cap!A2  cDim=1
MaxDupeErrors = 100
$offEcho
$call gdxxrw A-n6-k3-input-multi-vartemp-v2.2.xlsx @howToRead.txt
$gdxin A-n6-k3-input-multi-vartemp-v2.2.gdx


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
scalar R penalty rate for being late;
scalar EXP e;
scalars maxTemp,minTemp maximum and minimum temperature of trucks;
scalar maxTempCost refrigeration cost at max temp;
scalar costPerDegree refrigeration cost for lowering max temp by 1 degree;

$load W  M fm fi z R EXP maxTemp minTemp maxTempCost costPerDegree

parameter RLoad(k,b) matrix of  residual load for each truck with respect to different products;
parameter S(i,j) ETA matrix;
parameter D(j,b) demand at location j of product b;
parameter L(j) lower limit of time window at customer j;
parameter U(j) upper limit of time window at customer j;
parameter MSL(b) maximum shelf life of product b: days;
parameter Ys   0 if this is an initial planning run and 1 if this is a re-routing run;
parameter minSL(b) minimum acceptable shelf life;
parameters alpha(b), beta(b), gamma(b) coef. of door openings..transportation times..temperature;
parameter P(b) unit price of product b: AED per unit;
parameter Cap(k) Total capacity of each truck regardless of product;

$load RLoad S D L U MSL minSL alpha beta gamma P Ys Cap
$gdxin


Variables Cost total cost
          tCost cost for using trucks
          dCost cost for distance traveled
          qCost cost for quality lost
          eCost cost for arriving early
          lCost cost for arriving late
          sCost quality cost of surplus
          surplus(k,b) amount of surplus in truck k of product b; 
         

binary variables X(i,j,k) traveling from i to j using truck k
                 Y(k) using truck k
                 E(j) arriving early at location j
                 Q(j) arriving on time at location j
                 T(j) arriving late at location j;
                 
positive variable AR(k,j) arrival time of truck k at location j;
integer variables rank(j,k),rank(i,k) for subtour elimination constraint;
integer variable temp(k) temperature of truck k;
positive variable SL(j,k,b) shelf life of product b at customer j;
positive variable rg(k) refrigeration cost of truck k;
positive variable C(i,j,k) cost of traveling from i to j using truck k;


Equations OF objective function
          eq0 use trucks in order
          eq1, eq2, eq3, eq4, eq5 separate cost equations
          eq6, eq7 refrigeration and fuel costs
          eq8, eq9 Defintions of amount of surplus and quality cost of surplus
          const2 constraint 2: demand of trip is less than max load of truck
          const2a
          const3 constraint 3: all customer nodes must be entered once
          const4 constraint 4: all customer nodes must be left once
          const5 constraint 5: same truck to enter and leave node
          const6, const7 constraints 6&7: warehouse must be left and entered once per trip
          const8, const9, const10 constraints 8&9&10: further warehouse movement control
          const11, const11a constraint 11&11a: subtour elimination constraints
          const12, const12a
      
          const13 constraint 13: arrival time after lower bound
          const14, const15, const16, const17 constraints 13&14&15&16: setting time variables
          const18 constraint 18: shelf life calculations
          const19 constraint 19: minimum requirement for shelf life
          const20, const21 constraints 20&21: controlling truck temperature
          const22 Restricting the rank of DCE
          const24;
          
* Fixed Cost of Truck Hiring
eq1..    tCost =e= sum(k, W * Y(k));

*The cost of fuel and emissions while driving and while idling
eq7(i,j,k).. C(i,j,k) =e= (fm + rg(k)) * S(i,j)  + (fi + rg(k)) * sum(b, D(j,b))/z;
eq2..    dCost =e= sum((i,j,k), C(i,j,k) * X(i,j,k));

*Quality cost during transportation
eq3..    qCost =e= sum((i,j,k,b), P(b) *(MSL(b) - SL(j,k,b)) * D(j,b)/MSL(b) * X(i,j,k));

*Surplus Amount calculation 
eq8(k,b).. surplus(k,b)=e= Ys*Y(k)*(RLoad(k,b)- sum((sub2i(i),subj(j)),  D(j,b) * X(i,j,k)));


* Additional Quality Cost of Storing Surplus
eq9..    sCost=e=sum((k,b),surplus(k,b)*P(b)/MSL(b)*(MSL(b)*(1-EXP**(-alpha(b)*(rank('DCE',k))-beta(b)*(12+AR(k,'DCE')+surplus(k,b)/z)-gamma(b)*temp(k)))));


*Refrigeration cost per unit time
eq6(k)..     rg(k) =e= maxTempCost + (maxTemp - temp(k)) * costPerDegree;


**Penalties for missing retailer's time window
*Late Arrival Cost
eq5..    lCost =e= sum((sub2j(j),k), R * T(j) * (sum(b, D(j,b))/z + AR(k,j)- U(j)));
*Early Arrival Cost
eq4..    eCost =e= sum((subj(j),k), fi * (L(j) - AR(k,j)) *  E(j));



* OBJECTIVE FUNCTION
OF..      Cost =e= tCost + dCost + qCost+ eCost + lCost+ sCost;


*Shelf Life Equation
const18(j,k,b).. SL(j,k,b) =e= MSL(b) * (EXP ** (-1* alpha(b) * rank(j,k)))* (EXP ** (-1 * beta(b) * AR(k,j)))* (EXP ** (-1 * gamma(b) * temp(k)));                          
*Minimum Shelf Life Constraint
const19(subj(j),k,b).. SL(j,k,b) =g= minSL(b);


** Transportation Constraints

* Use trucks in order
eq0(k)..    Y(k+1) =l= Y(k);

* The total demand of a trip is less than the maximum load of the truck.
* (This is a redundant constraint in re-routing runs, but not in the initial planning run)
const2a(k)..sum((sub2i(i),subj(j),b),  D(j,b) * X(i,j,k)) =l= Cap(k) * Y(k);

* The total demand for each product on a trip is less than the current stock of that product on the truck .
*(This is a redundant constraint in initial planning, but not in re-routing runs).
const2(k,b).. sum((sub2i(i),subj(j)),  D(j,b) * X(i,j,k)) =l= RLoad(k,b) * Y(k);



*Each customer must be entered once, left once, and by the same truck
const3(subj(j)).. sum((sub2i(i), k), X(i,j,k)) =e= 1;
const4(subi(i)).. sum((sub2j(j), k), X(i,j,k)) =e= 1;
const5(subj(j),k).. sum(sub2i(i), X(i,j,k)) =e= sum(sub2j(i), X(j,i,k));

*The warehouse must be entered once and left once per trip
const6(k).. sum(subj(j), X('DCS',j,k)) =e= Y(k);
const7(k).. sum(subi(i), X(i,'DCE',k)) =e= Y(k);

*A trip cannot start from node N or end at node 0 or go directly from 0 to N.
const8.. sum((i,k), X(i,'DCS',k)) =e= 0;
const9.. sum((j,k), X('DCE',j,k)) =e= 0;
const10.. sum(k, X('DCS','DCE',k)) =e= 0;

* Subtour Elimination Constraints
const11(sub2j(j),sub2i(i),k).. rank(j,k) =g= rank(i,k) + N * X(i,j,k) - N + 1;
const22(k)..rank('DCE',k)=l=sum((sub2i(i),sub2j(j)),X(i,j,k));
const11a(k).. rank('DCS',k) =e= 0;

*Arrival Time Calculations

const12(sub2j(j),sub2i(i),k).. AR(k,j) =g= AR(k,i) + sum(b, D(i,b))/z + S(i,j) - (1 - X(i,j,k))* M;
const24(k,j)..AR(k,j)=l=M*sum(sub2i(i),X(i,j,k));
const12a(k).. AR(k,'DCS') =e= 0;


* Setting values of Late/Early/On-Time Arrival Indicators

const13(k,sub2j(j)).. AR(k,j) =l= L(j)  +  M*(1- E(j)) ;
const14(k,sub2j(j)).. AR(k,j) =g= U(j)  -  M*(1- T(j)) ;
const15(k,sub2j(j)).. AR(k,j) =g= L(j)  -  M*(1- Q(j)) ;
const16(k,sub2j(j)).. AR(k,j) =l= U(j)  +  M*(1- Q(j)) ;
const17(sub2j(j)).. E(j) + Q(j) + T(j) =e= 1;


* Truck Temperature Constraints
const20(k).. temp(k)=g= minTemp;
const21(k).. temp(k)=l= maxTemp;




model simple /all/;
solve simple using MINLP minimizing Cost;


execute_unload 'A-n6-k3-input-multi-vartemp-v2.2.gdx', D L U x  S MSL SL Ys surplus sCost E T Q AR Cost qCost tCost dCost lCost eCost rank RLoad Cap;

$onEcho > howToWrite.txt
text="Customers Demands:"        rng=Sheet1!A1
squeeze=n        par=D           rng=Sheet1!A2
text="Customers Time Windows:"   rng=Sheet1!A10
text="Lower limit:"              rng=Sheet1!A11
squeeze=n        par=L           rng=Sheet1!A12
text="Upper limit:"              rng=Sheet1!A13
squeeze=n        par=U           rng=Sheet1!A14
text="Solution Routes:"          rng=Sheet1!A16
var=x.l                          rng=Sheet1!A19
text="ETA Matrix:"               rng=Sheet2!A1
squeeze=n        par=S           rng=Sheet2!A2
text="Maximum Shelf Lives:"      rng=Sheet2!A13
squeeze=n        par=MSL         rng=Sheet2!A14
text="Shelf Lives:"              rng=Sheet3!A1
squeeze=n        var=SL          rng=Sheet3!A2
text="Re-routing Run:"           rng=Sheet4!A1
squeeze=n       par=Ys           rng=Sheet4!B1
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
var=rank                            rng=Sheet6!A14
$offEcho
execute 'gdxxrw A-n6-k3-input-multi-vartemp-v2.2.gdx output=A-n6-k3-output-multi-vartemp-v2.2.xlsx @howToWrite.txt';
