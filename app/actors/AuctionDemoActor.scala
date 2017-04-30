package actors

import javax.inject.Inject

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.postfixOps
import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorSystem, Cancellable, Scheduler}
import akka.util.Timeout
import models._

import scala.util.{Failure, Random, Success}

object AuctionDemoActor {

  case object AuctionAction
  case object NewBidder
  case object NewItem
  case object NewWinningBid
  case object NewPayment

  case object StartMeUp
  case object ShutMeDown
}

class AuctionDemoActor @Inject()(actorSystem: ActorSystem, itemHandler: ItemHandler, bidderHandler: BidderHandler) extends Actor with ActorLogging {
  import AuctionDemoActor._

  private val scheduler = actorSystem.scheduler

  implicit val timeout = Timeout(3 seconds)

  val random = new Random()

  var bidderData: List[BidderData] = List.empty
  var itemData: List[ItemData] = List.empty

  private var runningSchedule: Cancellable = _

  def startMeUp(): Unit = {
    Range(0, 10) foreach { _ => self ! NewBidder }
    Range(0, 30) foreach { _ => self ! NewItem }

    runningSchedule = scheduler.schedule(10 seconds, 10 seconds, self, AuctionAction)
  }

  def shutMeDown(): Unit = {
    if(runningSchedule != null && !runningSchedule.isCancelled) {
      runningSchedule.cancel()
      runningSchedule = null
    }
  }

  override def receive = {
    case StartMeUp =>
      startMeUp()

    case ShutMeDown =>
      shutMeDown()
/*
    case AuctionAction =>
      random.nextInt(10) match {
        case 0 =>
          self ! NewBidder
        case 1 =>
          self ! NewItem
        case 2 =>
          self ! NewPayment
        case 3 =>
          self ! NewWinningBid
        case _ =>
      }

    case NewBidder =>
      Names.nextBidder() foreach { newBidder =>
        log.debug("Adding bidder: {}", newBidder)
        bidderHandler.create(newBidder) onComplete {
          case Success(bidder) =>
            bidderHandler.currentBidders() foreach { bd =>
              bidderData = bd
              log.debug("Added bidder: {}  Bidder Data size: {}", bidder, bidderData.size)
            }
          case Failure(e) =>
            log.warning(s"Unable to add bidder: ${e.getMessage}")
        }
      }

    case NewItem =>
      Items.nextItem() foreach { newItem =>
        log.debug("Adding item: {}", newItem)
        itemHandler.create(newItem._1, newItem._2, newItem._3, newItem._4, newItem._5, newItem._6) onComplete {
          case Success(item) =>
            itemHandler.currentItems() foreach { id =>
              itemData = id
              log.debug("Added item: {}  Item Data size: {}", item, itemData.size)
            }
          case Failure(e) =>
            log.warning(s"Unable to add item: ${e.getMessage}")
        }
      }

    case NewPayment =>
      bidderHandler.currentBidders() foreach { bd =>
        bidderData = bd

        val bidderIdx = random.nextInt(bidderData.size)
        val bidder = bidderData(bidderIdx)
        val bidderId = bidder.bidder.id.get
        bidderHandler.totalOwed(bidderId) onComplete {
          case Success(amount) if amount > 0 =>
            val description = random.nextInt(3) match {
              case 0 => "Cash"
              case 1 => "Check #10" + amount.intValue()
              case 2 => "Credit Card - Auth #" + random.nextInt(10000000).formatted("%07d") + "" + amount.intValue()
            }
            log.debug("Adding payment for bidder {} in amount {} with description {}", bidder.bidder, amount, description)
            bidderHandler.addPayment(bidderId, description, amount) onComplete {
              case Success(payment) =>
                bidderHandler.currentBidders() foreach { bd =>
                  bidderData = bd
                  log.debug("Added payment for bidder {} in amount {} with description {}  Bidder Data Size: {}", payment.bidder, payment.amount, payment.description, bidderData.size)
                }
              case Failure(e) =>
                log.warning(s"Unable to add payment: ${e.getMessage}")
            }
          case Failure(e) =>
            log.warning(s"Unable to get total owed for bidder $bidderId")
          case _ =>
        }
      }

    case NewWinningBid =>
      bidderHandler.currentBidders().zip(itemHandler.currentItems()) foreach { case (bd, id) =>
        bidderData = bd
        itemData = id

        val bidderIdx = random.nextInt(bidderData.size)
        val bidder = bidderData(bidderIdx)
        val itemIdx = random.nextInt(itemData.size)
        val item = itemData(itemIdx)
        val amount = item.item.minbid + random.nextInt(300)
        log.debug("Adding winning bid for bidder {} for item {} in amount {}", bidder.bidder, item.item, amount)
        itemHandler.addWinningBid(bidder.bidder, item.item, amount) onComplete {
          case Success(winningBid) =>
            itemHandler.currentItems() foreach { id =>
              itemData = id
              log.debug("Added winning bid for bidder {} for item {} in amount {}  Item Data Size: {}", winningBid.bidder, winningBid.item, winningBid.amount, itemData.size)
            }
          case Failure(e) =>
            log.warning(s"Unable to add winning bid: ${e.getMessage}")
        }
      }
*/
    case _ =>
  }
}

object Names {
  val rawNames = "Smith Johnson Williams Jones Brown Davis Miller Wilson Moore Taylor Anderson Thomas Jackson White Harris Martin Thompson Garcia Martinez Robinson Clark Rodriguez Lewis Lee Walker Hall Allen Young Hernandez King Wright Lopez Hill Scott Green Adams Baker Gonzalez Nelson Carter Mitchell Perez Roberts Turner Phillips Campbell Parker Evans Edwards Collins Stewart Sanchez Morris Rogers Reed Cook Morgan Bell Murphy Bailey Rivera Cooper Richardson Cox Howard Ward Torres Peterson Gray Ramirez James Watson Brooks Kelly Sanders Price Bennett Wood Barnes Ross Henderson Coleman Jenkins Perry Powell Long Patterson Hughes Flores Washington Butler Simmons Foster Gonzales Bryant Alexander Russell Griffin Diaz Hayes Myers Ford Hamilton Graham Sullivan Wallace Woods Cole West Jordan Owens Reynolds Fisher Ellis Harrison Gibson Mcdonald Cruz Marshall Ortiz Gomez Murray Freeman Wells Webb Simpson Stevens Tucker Porter Hunter Hicks Crawford Henry Boyd Mason Morales Kennedy Warren Dixon Ramos Reyes Burns Gordon Shaw Holmes Rice Robertson Hunt Black Daniels Palmer Mills Nichols Grant Knight Ferguson Rose Stone Hawkins Dunn Perkins Hudson Spencer Gardner Stephens Payne Pierce Berry Matthews Arnold Wagner Willis Ray Watkins Olson Carroll Duncan Snyder Hart Cunningham Bradley Lane Andrews Ruiz Harper Fox Riley Armstrong Carpenter Weaver Greene Lawrence Elliott Chavez Sims Austin Peters Kelley Franklin Lawson Fields Gutierrez Ryan Schmidt Carr Vasquez Castillo Wheeler Chapman Oliver Montgomery Richards Williamson Johnston Banks Meyer Bishop McCoy Howell Alvarez Morrison Hansen Fernandez Garza Harvey Little Burton Stanley Nguyen George Jacobs Reid Kim Fuller Lynch Dean Gilbert Garrett Romero Welch Larson Frazier Burke Hanson Day Mendoza Moreno Bowman Medina Fowler Brewer Hoffman Carlson Silva Pearson Holland Douglas Fleming Jensen Vargas Byrd Davidson Hopkins May Terry Herrera Wade Soto Walters Curtis Neal Caldwell Lowe Jennings Barnett Graves Jimenez Horton Shelton Barrett O'Brien Castro Sutton Gregory McKinney Lucas Miles Craig Rodriquez Chambers Holt Lambert Fletcher Watts Bates Hale Rhodes Pena Beck Newman Haynes McDaniel Mendez Bush Vaughn Parks Dawson Santiago Norris Hardy Love Steele Curry Powers Schultz Barker Guzman Page Munoz Ball Keller Chandler Weber Leonard Walsh Lyons Ramsey Wolfe Schneider Mullins Benson Sharp Bowen Daniel Barber Cummings Hines Baldwin Griffith Valdez Hubbard Salazar Reeves Warner Stevenson Burgess Santos Tate Cross Garner Mann Mack Moss Thornton Dennis Mcgee Farmer Delgado Aguilar Vega Glover Manning Cohen Harmon Rodgers Robbins Newton Todd Blair Higgins Ingram Reese Cannon Strickland Townsend Potter Goodwin Walton Rowe Hampton Ortega Patton Swanson Joseph Francis Goodman Maldonado Yates Becker Erickson Hodges Rios Conner Adkins Webster Norman Malone Hammond Flowers Cobb Moody Quinn Blake Maxwell Pope Floyd Osborne Paul McCarthy Guerrero Lindsey Estrada Sandoval Gibbs Tyler Gross Fitzgerald Stokes Doyle Sherman Saunders Wise Colon Gill Alvarado Greer Padilla Simon Waters Nunez Ballard Schwartz McBride Houston Christensen Klein Pratt Briggs Parsons McLaughlin Zimmerman French Buchanan Moran Copeland Roy Pittman Brady McCormick Holloway Brock Poole Frank Logan Owen Bass Marsh Drake Wong Jefferson Park Morton Abbott Sparks Patrick Norton Huff Clayton Massey Lloyd Figueroa Carson Bowers Roberson Barton Tran Lamb Harrington Casey Boone Cortez Clarke Mathis Singleton Wilkins Cain Bryan Underwood Hogan McKenzie Collier Luna Phelps McGuire Allison Bridges Wilkerson Nash Summers Atkins Wilcox Pitts Conley Marquez Burnett Richard Cochran Chase Davenport Hood Gates Clay Ayala Sawyer Roman Vazquez Dickerson Hodge Acosta Flynn Espinoza Nicholson Monroe Wolf Morrow Kirk Randall Anthony Whitaker O'Connor Skinner Ware Molina Kirby Huffman Bradford Charles Gilmore Dominguez O'Neal Bruce Lang Combs Kramer Heath Hancock Gallagher Gaines Shaffer Short Wiggins Mathews McClain Fischer Wall Small Melton Hensley Bond Dyer Cameron Grimes Contreras Christian Wyatt Baxter Snow Mosley Shepherd Larsen Hoover Beasley Glenn Petersen Whitehead Meyers Keith Garrison Vincent Shields Horn Savage Olsen Schroeder Hartman Woodard Mueller Kemp Deleon Booth Patel Calhoun Wiley Eaton Cline Navarro Harrell Lester Humphrey Parrish Duran Hutchinson Hess Dorsey Bullock Robles Beard Dalton Avila Vance Rich Blackwell York Johns Blankenship Trevino Salinas Campos Pruitt Moses Callahan Golden Montoya Hardin Guerra McDowell Carey Stafford Gallegos Henson Wilkinson Booker Merritt Miranda Atkinson Orr Decker Hobbs Preston Tanner Knox Pacheco Stephenson Glass Rojas Serrano Marks Hickman English Sweeney Strong Prince McClure Conway Walter Roth Maynard Farrell Lowery Hurst Nixon Weiss Trujillo Ellison Sloan Juarez Winters McLean Randolph Leon Boyer Villarreal McCall Gentry Carrillo Kent Ayers Lara Shannon Sexton Pace Hull Leblanc Browning Velasquez Leach Chang House Sellers Herring Noble Foley Bartlett Mercado Landry Durham Walls Barr McKee Bauer Rivers Everett Bradshaw Pugh Velez Rush Estes Dodson Morse Sheppard Weeks Camacho Bean Barron Livingston Middleton Spears Branch Blevins Chen Kerr McConnell Hatfield Harding Ashley Solis Herman Frost Giles Blackburn William Pennington Woodward Finley McIntosh Koch Best Solomon McCullough Dudley Nolan Blanchard Rivas Brennan Mejia Kane Benton Joyce Buckley Haley Valentine Maddox Russo McKnight Buck Boon McMillan Crosby Berg Dotson Mays Roach Church Chan Richmond Meadows Faulkner O'Neill Knapp Kline Barry Ochoa Jacobson Gay Avery Hendricks Horne Shepard Hebert Cherry Cardenas McIntyre Whitney Waller Holman Donaldson Cantu Terrell Morin Gillespie Fuentes Tillman Sanford Bentley Peck Key Salas Rollins Gamble Dickson Battle Santana Cabrera Cervantes Howe Hinton Hurley Spence Zamora Yang McNeil Suarez Case Petty Gould McFarland Sampson Carver Bray Rosario Macdonald Stout Hester Melendez Dillon Farley Hopper Galloway Potts Bernard Joyner Stein Aguirre Osborn Mercer Bender Franco Rowland Sykes Benjamin Travis Pickett Crane Sears Mayo Dunlap Hayden Wilder McKay Coffey McCarty Ewing Cooley Vaughan Bonner Cotton Holder Stark Ferrell Cantrell Fulton Lynn Lott Calderon Rosa Pollard Hooper Burch Mullen Fry Riddle Levy David Duke O'Donnell Guy Michael Britt Frederick Daugherty Berger Dillard Alston Jarvis Frye Riggs Chaney Odom Duffy Fitzpatrick Valenzuela Merrill Mayer Alford McPherson Acevedo Donovan Barrera Albert Cote Reilly Compton Raymond Mooney McGowan Craft Cleveland Clemons Wynn Nielsen Baird Stanton Snider Rosales Bright Witt Stuart Hays Holden Rutledge Kinney Clements Castaneda Slater Hahn Emerson Conrad Burks Delaney Pate Lancaster Sweet Justice Tyson Sharpe Whitfield Talley Macias Irwin Burris Ratliff McCray Madden Kaufman Beach Goff Cash Bolton McFadden Levine Good Byers Kirkland Kidd Workman Carney Dale McLeod Holcomb England Finch Head Burt Hendrix Sosa Haney Franks Sargent Nieves Downs Rasmussen Bird Hewitt Lindsay Le Foreman Valencia O'Neil Delacruz Vinson Dejesus Hyde Forbes Gilliam Guthrie Wooten Huber Barlow Boyle McMahon Buckner Rocha Puckett Langley Knowles Cooke Velazquez Whitley Noel Vang"
  val names = rawNames.split(" ")

  private val nextNameIndex = Range(0, names.length).toIterator

  def nextBidder(): Option[String] = {
    if(nextNameIndex.hasNext)
      Some(names(nextNameIndex.next()))
    else
      None
  }
}

object Items {
  val rawItems =
    """
      501,Basket,Basket - Chill Out,0,0
      502,Basket,Doggie Basket,0,0
      503,Basket,All About Music,0,0
      504,Basket,Basket - Beers around the world,0,0
      505,Basket,Ice Shaver,0,0
      506,Basket,Basket of Books/Russian Doll,0,0
      507,Basket,Fit Bit Basket,0,0
      508,Basket,Spa Basket,0,0
      509,Basket,4 Frilly neck scarves,0,0
      510,Basket,Basket - Pool/Beach Theme,0,0
      901,Dessert,Lemon Blueberry Layer Cake,0,0
      903,Dessert,Cookies and Cream Cake,0,0
      906,Dessert,Texas White Sheet Cake,0,0
      905,Dessert,Upside Down Double Chocolate Jack Daniels Cheesecake,0,0
      909,Dessert,Granny Anne's German Chocolate Cake,0,0
      907,Dessert,Chocolate Chip Cookie Cheesecake,0,0
      902,Dessert,Not Your Nana's Banana Pudding,0,0
      904,Dessert,Gluten & Dairy Free Chocolate Oreo Cookie Bundt Cake,0,0
      908,Dessert,The World's Best Strawberry Cake,0,0
      101,Item-Apparel,24 Screen Print Tees,50,0
      102,Item-Apparel,Bling hats,5,0
      103,Item-Apparel,Embroidered ladies cross t-shirt,20,0
      104,Item-Apparel,Handbag,10,0
      105,Item-Apparel,Brown/white Frilly Scarf,5,0
      106,Item-Apparel,FMHS Frilly scarf (blue/white),5,0
      107,Item-Apparel,MHS Frilly Scarf (red/white),5,0
      108,Item-Apparel,Teal frilly scarf,5,0
      109,Item-Apparel,Knit Scarves (3),10,0
      110,Item-Apparel,5 Thin loop Scarves,10,0
      111,Item-Apparel,Hooded Baby Blanket,15,0
      112,Item-Apparel,The Rusty Rabbit $25 Gift Certificate,10,0
      113,Item-Food,Basket - New Mexico Wine and Pasta Dinner Value $110,10,0
      114,Item-Food,Basket of homemade breads,5,0
      115,Item-Food,Chocolate Indulgence Bouquet Value $49,15,0
      116,Item-Food,Basket - family movie night Value $100,15,0
      117,Item-Food,Basket - wine and such,15,0
      118,Item-Food,Basket - all things chocolate,20,0
      119.1,Item-Food,Homemade Salsa (28oz jar),10,0
      119.2,Item-Food,Homemade Salsa (28oz jar),10,0
      119.3,Item-Food,Homemade Salsa (28oz jar),10,0
      120,Item-Food,Nothing Bundt Cakes 1 10" Decorated Cake $40 Value,10,0
      121,Item-Home,Print of original watercolor "Communion II",30,0
      122,Item-Home,Decorative Window,20,0
      123,Item-Home,Easter Basket Flower Arrangement,5,0
      124,Item-Home,Kinkade framed prints,40,0
      125,Item-Home,Kinkade framed prints,40,0
      126,Item-Home,Spring Floral Arrangement,5,0
      127,Item-Home,Golf scentsy warmer,5,0
      128,Item-Home,Sewing Machine Singer Model 2662,50,0
      129,Item-Home,Roku,35,0
      130,Item-Home,Pair of Bunnies,15,0
      131,Item-Home,Pair of Lambs,5,0
      132,Item-Home,Pie Rack,15,0
      133,Item-Home,Flower arrangement -Fall,10,0
      134,Item-Home,Flower arrangement -Hanging,5,0
      135,Item-Home,Chairs(2),30,0
      136,Item-Home,Vase,10,0
      137,Item-Jewelry,$20 Gift Card & Silver Charm Bracelet,15,0
      138,Item-Jewelry,Bracelet,5,0
      139,Item-Jewelry,Travel jewelry keeper,5,0
      140,Item-Jewelry,Charm Neclace,10,0
      141,Item-Jewelry,Gear Earrings,8,0
      142,Item-Jewelry,Jewelry Set,20,0
      143,Item-Misc,Receiver,30,0
      144,Item-Misc,City Florist Gift Card Value $25,10,0
      145,Item-Misc,Large Cross -Red,10,0
      146,Item-Misc,Large Cross -Teal,10,0
      147,Item-Misc,Small Cross -Blue,10,0
      148,Item-Misc,Small Cross -Teal,10,0
      149,Item-Misc,Head of the Line Plate,10,0
      150,Item-Misc,Lance Berkman autographed baseball,15,0
      151,Item-Misc,Homemade Zentangle note card (10),5,0
      152,Item-Misc,Kimball Museum Art Lovers Basket Value over $80,10,0
      153,Item-Misc,Piano($300.00 +fees),300,0
      154,Item-Misc,TCU items,25,0
      155,Item-Misc,American Girl Doll Clothes,10,0
      156,Item-Outdoor,Japanese Maple Tree,10,0
      157,Item-Outdoor,Stepping stone,8,0
      158,Item-Outdoor,Planter,20,0
      159,Item-Outdoor,Turtle,10,0
      160,Item-Outdoor,Mexican boot planter Value $60,10,0
      161,Item-Personal Care,Sonicare Toothbrush $80,20,0
      162,Item-Personal Care,MK gift certificate Value $125,30,0
      163,Item-Personal Care,Luke 's Locker $25 Gift Card,10,0
      164,Item-Personal Care,Luke 's Locker $25 Gift Card,10,0
      165,Item-Personal Care,Luke 's Locker $25 Gift Card,10,0
      166,Item-Personal Care,Family Physician Kit,20,0
      167,Item-Personal Care,Introduction to essential oils kit,5,0
      168,Item-Personal Care,Arbonne bath gel body lotion scrub,15,0
      201,Outing,Adventure Landing (Coit Rd in Dallas) 4 free games of miniature golf,10,0
      202,Outing,Adventure Landing (Coit Rd in Dallas) 4 free games of miniature golf,10,0
      203,Outing,AT & T Performing Arts Center 2 tickets and self -parking pass to Beauty & the Beast 4 / 15 / 14@8 pm,20,0
      204,Outing,Two Rangers tickets for April 12,35,0
      205,Outing,Dallas Summer Musicals Admission for 4 to Mamma Mia !6 / 3 / 14 Value $320,100,0
      206,Outing,Dallas Zoo 2 Adult & 2 Youth Tickets Value $54,10,0
      207,Outing,Excite Mini Party (1 hour for 15 children),50,0
      208.1,Outing,Dinner on the Patio,20,0
      208.2,Outing,Dinner on the Patio,20,0
      208.3,Outing,Dinner on the Patio,20,0
      209,Outing,FW Museum of Science & Industry 2 Free passes to Museum and Noble Planetarium,10,0
      210,Outing,FW Zoo Admission for 2 Value up to $24,10,0
      211,Outing,George Bush National Library 2 Free Passes Value $32,10,0
      212,Outing,Ice-Skating Center Ice Skating Lessons $84,10,0
      213,Outing,Institute of Texan Cultures Free General Admission for Family of 4 Value $32,5,0
      214.1,Outing,Steak dinner for 2,20,0
      214.2,Outing,Steak dinner for 2,20,0
      214.3,Outing,Steak dinner for 2,20,0
      215,Outing,Modern Art Museum of Fort Worth 2 Free Admission $40 Value,10,0
      216,Outing,Cosmic Jump 2-hour passes (4),10,0
      217,Outing,NRH2O Two 1 -day passes Value $50,10,0
      218.1,Outing,Morning fishing trip to Ray Roberts plus lunch,20,0
      218.2,Outing,Morning fishing trip to Ray Roberts plus lunch,20,0
      219,Outing,Rockin 'R' River Rides Raft or Toob Trip for 4 Adults Value $140,50,0
      220,Outing,Scarborough Fair Admission for 2 Adults & 2 Youth Value $68,10,0
      221,Outing,Texas Rangers Baseball 2 Upper Reserved Tickets,10,0
      222,Outing,5 course dinner for six with wine / beer pairing,40,0
      223,Outing,Dallas Symphony Orchestra 2 tickets to choice of TI Classical Series,40,0
      301,Restaurant,Bahama Bucks $30 Gift Pack,10,0
      302,Restaurant,Bahama Bucks $30 Gift Pack,10,0
      303,Restaurant,Bahama Bucks $30 Gift Pack,10,0
      304,Restaurant,Baskin Robbins Two Family 4 Pack Coupons,5,0
      305,Restaurant,Baskin Robbins Two Family 4 Pack Coupons,5,0
      306,Restaurant,Big Fish Grill & Bar $25 Gift Certicate,10,0
      307,Restaurant,Cold Stone Creamery $15 Gift Card,5,0
      308,Restaurant,Cotton Patch Dinner for 2 Value $22,10,0
      309,Restaurant,Firehouse Subs (5) $10 Gift Cards,10,0
      310,Restaurant,Patrizio $50 Gift Certificate,15,0
      311,Restaurant,Potbelly Sandwich Shop 5 Free Sandwiches,10,0
      312,Restaurant,Potbelly Sandwich Shop 5 Free Sandwiches,10,0
      313,Restaurant,Rockfish $20 Gift Cards,10,0
      314,Restaurant,Rockfish $20 Gift Cards,10,0
      401,Service,College Shirt Challenge,5,0
      402,Service,Three Course Italian Dinner,20,0
      404,Service,Bluebonnet Fences 1 - Free Post Replacement Value $100,50,0
      403,Service,Bluebonnet Fences 1 - Free gate with purchase of new fence Value $100,50,0
      405,Service,Elite Strength & Conditioning $100 Voucher toward any session or camp,30,0
      406,Service,Foster Chiropractic $100 Gift Certificate,30,0
      407,Service,4 hours of Handyman,20,0
      408,Service,Family Photo Session,30,0
      409,Service,DVD slide show creation,30,0
      410,Service,Messina Shoe Repair $40 Gift Certificate,15,0
      411,Service,Romantic Dinner for Two,20,0
      413,Service-Child,Big Sister for the Day,10,0
      414,Service-Child,12 hours of child care,10,0
      415,Service-Child,Big Brothers for the Day,10,0
      416,Service-Child,5 hours of child care,10,0
      417,Service-Child,Big Sister for the Day,10,0
      701,Guess Jar,Visa Gift card,10,0
      702,Treasure Chest,Treasure chest AMC card and Blue Goose card,0,0
    """.stripMargin

  val itemLineRegex = """^\s*([^,]+),([^,]+),([^,]+),(\d+),(\d+)$""".r("itemNum", "category", "description", "minBid", "estValue")

  val itemData = for {
    (itemLine, bidderName) <- rawItems.split("\n").zip(Names.names)
    itemLineRegex(itemNum, category, description, minBid, estValue) <- itemLineRegex findFirstIn itemLine
  } yield (itemNum, category, bidderName, description, minBid.toDouble, estValue.toDouble)

  private val nextItemIndex = Range(0, itemData.length).toIterator

  def nextItem(): Option[(String, String, String, String, Double, Double)] = {
    if(nextItemIndex.hasNext)
      Some(itemData(nextItemIndex.next()))
    else
      None
  }
}