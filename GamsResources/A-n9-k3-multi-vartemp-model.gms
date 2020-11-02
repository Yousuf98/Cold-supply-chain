option reslim = 1000;
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
par=N      rng=N!A2      dim=0
par=W      rng=W!A2      dim=0
par=Lk     rng=Lk!A2     dim=0
par=M      rng=M!A2      dim=0
par=fm     rng=fm!A2     dim=0
par=fi     rng=fi!A2     dim=0
par=v      rng=v!A2      dim=0
par=z      rng=z!A2      dim=0
par=R      rng=R!A2      dim=0
par=EXP    rng=EXP!A2    dim=0
par=maxTemp       rng=maxTemp!A2       dim=0
par=minTemp       rng=minTemp!A2       dim=0
par=maxTempCost   rng=maxTempCost!A2   dim=0
par=costPerDegree rng=costPerDegree!A2 dim=0
MaxDupeErrors = 100
$offEcho
$call gdxxrw A-n9-k3-input-multi-vartemp.xlsx @howToRead.txt
$gdxin A-n9-k3-input-multi-vartemp.gdx

Set i all locations;
alias(i,j);
set subi(i) customers;
set sub2i(i) starting warehouse and customers;
set subj(j)  customers;
set sub2j(j) customers and ending warehouse;
set k trucks;
set b products;

$load i subi sub2i subj sub2j k b

scalar N total number of nodes;
scalar W fixed cost for using a truck: AED per truck;
scalar Lk maximum load of trck: units;
scalar M large positive number;
scalar fm fuel cost for a moving truck: AED per hour;
scalar fi fuel cost for an idle truck: AED per hpur;
scalar v average speed of trucks: km per hour;
scalar z uloading speed: units per hour;
scalar R penalty rate for being late;
scalar EXP e;
scalars maxTemp,minTemp maximum and minimum temperature of trucks;
scalar maxTempCost refrigeration cost at max temp;
scalar costPerDegree refrigeration cost for lowering max temp by 1 degree;

$load N W Lk M fm fi v z R EXP maxTemp minTemp maxTempCost costPerDegree

parameter S(i,j) distances matrix;
parameter D(j,b) demand at location j of product b;
parameter L(j) lower limit of time window at customer j;
parameter U(j) upper limit of time window at customer j;
parameter MSL(b) maximum shelf life: days;
parameter minSL(b) minimum acceptable shelf life;
parameters alpha(b), beta(b), gamma(b) coef. of door openings..transportation times..temperature;
parameter P(b) unit price of product b: AED per unit;

$load S D L U MSL minSL alpha beta gamma p
$gdxin


Variables Cost total cost
          tCost cost for using trucks
          dCost cost for distance traveled
          qCost cost for quality lost
          banana     q cost banana
          strawberry q cost strawberry
          broccoli   q cost broccoli
          eCost cost for arriving early
          lCost cost for arriving late;

binary variables X(i,j,k) traveling from i to j using truck k
                 Y(k) using truck k
                 E(j) arriving early at location j
                 Q(j) arriving on time at location j
                 T(j) arriving late at location j;
positive variable A(j) arrival time at location j;
integer variables rank(j),rank(i) for subtour constraint;
integer variable temp(k) temperature of truck k;
positive variable SL(j,k,b) shelf life of product b at customer j;

positive variable rg(k) refrigeration cost of truck k;
positive variable C(i,j,k) cost of traveling from i to j using truck k;


Equations OF objective function
          eq1, eq2, eq3, eq4, eq5 separate cost equations
          eq6, eq7 refrigeration and fule costs
          const1 constraint 1: if truck is used set to 1
          const2 constraint 2: demand of trib is less than max load of truck
          const3 constraint 3: all customer nodes must be entered once
          const4 constraint 4: all custmoer nodes must be left once
          const5 constraint 5: same truck to enter and leave node
          const6, const7 constraints 6&7: warehouse must be left and entered once per trip
          const8, const9, const10 constraints 8&9&10: further warehouse movement control
          const11, const11a constraint 11&11a: subtour elimination constraints
          const12, const12a constraint 12&12a: arrival time equations
          const13 constraint 13: arrival time after lower bound
          const13, const14, const15, const16, const17 constraints 13&14&15&16: setting time variables
          const18 constraint 18: shlef life calculations
          const19 constraint 19: minimum requirement for shelf life
          const20, const21 constraints 20&21: controlling truck temperature;

eq1..    tCost =e= sum(k, (W * Y(k)));
eq2..    dCost =e= sum((i,j,k), C(i,j,k) * X(i,j,k));
eq3..    qCost =e= sum((i,j,k,b), P(b) *(MSL(b) - SL(j,k,b)) * D(j,b)/MSL(b) * X(i,j,k));
eq4..    eCost =e= sum(subj(j), fi * (L(j) - A(j)) *  E(j));
eq5..    lCost =e= sum(subj(j), R * T(j) * ((sum(b, D(j,b))/z ) + A(j)- U(j)));
OF..      Cost =e= tCost + dCost + qCost + eCost + lCost;

eq6(k)..     rg(k) =e= maxTempCost + (maxTemp - temp(k)) * costPerDegree;
eq7(i,j,k).. C(i,j,k) =e= (fm + rg(k)) * S(i,j)  + (fi + rg(k)) * (sum(b, D(j,b)))/z;

const1(k).. sum((subi(i),subj(j)), X(i,j,k)) =l= N * Y(k);
const2(k).. sum((sub2i(i),subj(j),b),  D(j,b) * X(i,j,k)) =l= Lk * Y(k);
const3(subj(j)).. sum((sub2i(i), k), X(i,j,k)) =e= 1;
const4(subi(i)).. sum((sub2j(j), k), X(i,j,k)) =e= 1;
const5(subj(j),k).. sum(sub2i(i), X(i,j,k)) =e= sum(sub2j(i), X(j,i,k));
const6(k).. sum(subj(j), X('DCS',j,k)) =e= Y(k);
const7(k).. sum(subi(i), X(i,'DCE',k)) =e= Y(k);
const8.. sum((i,k), X(i,'DCS',k)) =e= 0;
const9.. sum((j,k), X('DCE',j,k)) =e= 0;
const10.. sum(k, X('DCS','DCE',k)) =e= 0;
const11a.. rank('DCS') =e= 0;
const11(sub2j(j),sub2i(i),k).. rank(j) =g= rank(i) + N * X(i,j,k) - N + 1;

const12a.. A('DCS') =e= 0;
const12(sub2j(j),sub2i(i),k).. A(j) =g= A(i) + (sum(b, D(i,b))/z) + (S(i,j)) - (1 - X(i,j,k))* M;


const13(j).. A(j) =l= L(j)  +  M*(1- E(j)) ;
const14(j).. A(j) =g= U(j)  -  M*(1- T(j)) ;
const15(j).. A(j) =g= L(j)  -  M*(1- Q(j)) ;
const16(j).. A(j) =l= U(j)  +  M*(1- Q(j)) ;
const17(j).. E(j) + Q(j) + T(j) =e= 1;

const18(j,k,b).. SL(j,k,b) =e= MSL(b) * (EXP ** (-1* alpha(b) * rank(j)))
                           * (EXP ** (-1 * beta(b) * A(j)))
                           * (EXP ** (-1 * gamma(b) * temp(k)));

const19(subj(j),k,b).. SL(j,k,b) =g= minSL(b);

const20(k).. temp(k)=g= minTemp;
const21(k).. temp(k)=l= maxTemp;

model simple /all/;
solve simple using MINLP minimizing Cost;
display x.l, y.l, temp.l, A.l, E.l, T.l, rank.l, SL.l;
display Cost.l, tCost.l, dCost.l, qCost.l, eCost.l, lCost.l;

execute_unload 'A-n9-k3-input-multi-vartemp.gdx', x Cost A D L U;
$onEcho > howToWrite.txt
text="Customers Demands:"        rng=A1
squeeze=n        par=D           rng=A2
text="Customers Time Windows:"   rng=A4
text="Lower limit:"              rng=A5
squeeze=n        par=L           rng=A6
text="Upper limit:"              rng=A8
squeeze=n        par=U           rng=A9
text="Total cost:"               rng=A11
var=Cost.l                       rng=A12
text="Solution Routes:"          rng=A13
var=x.l                          rng=A14
$offEcho
execute 'gdxxrw A-n9-k3-input-multi-vartemp.gdx output=A-n9-k3-output-multi-vartemp.xlsx @howToWrite.txt';
