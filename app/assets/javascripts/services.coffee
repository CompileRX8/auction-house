define ['angular'],
  (angular) ->
    mod = angular.module('AuctionHouse.services', [])

    mod.factory 'userService', ['$http', '$q',
      ($http, $q) ->
        {
          loginUser: (credentials) ->
            $http.post('/login', credentials).then (response) ->
              response.data

          logoutUser: (token) ->
            $http.post('/logout', { token: token }).then (response) ->
              response.data
        }
      ]

    mod.factory 'dataService', ['$http', '$interval',
      ($http, $interval) ->
        new class DataService
          @count: 0

          constructor: () ->
            @_myid = ++DataService.count

            @biddersdata = {}
            @itemsdata = {}
            @winningbids = []
            @totalBids = 0

            @_lastUpdate = Date.now()

            updateBidders = (msg) =>
              @_lastUpdate = Date.now()
              console.log "Received new biddersdata: " + msg
              @biddersdata = @_parseMsg(msg)

            @_biddersFeed = new EventSource("/biddersFeed")
            @_biddersFeed.addEventListener("message", updateBidders, false)

            updateItems = (msg) =>
              @_lastUpdate = Date.now()
              console.log "Received new itemsdata: " + msg
              @itemsdata = @_parseMsg(msg)

            @_itemsFeed = new EventSource("/itemsFeed")
            @_itemsFeed.addEventListener("message", updateItems, false)

            callPush = () =>
              now = Date.now()
              timeSinceLastUpdate = now - @_lastUpdate
              if timeSinceLastUpdate > 15000
                $http.get('/pushBidders')
                $http.get('/pushItems')
                console.log "Call Push"

            @_updateInterval = $interval(callPush, 3000)

            console.log "Created DataService"

          _updateTotalBids: (thingsdata) =>
            totalBids = 0
            winningbids = []
            for thingdata in thingsdata
              for winningbid in thingdata.winningBids
                winningbids.push(winningbid)
                totalBids += winningbid.amount
            @totalBids = totalBids
            @winningbids = winningbids
            console.log [@_myid, "of", DataService.count, ":", @totalBids, "=", totalBids, " ", @winningbids.length, "=", winningbids.length]

          _parseMsg: (msg) =>
            parsedData = JSON.parse(msg.data)
            @_updateTotalBids(parsedData)
            parsedData

          hasWinningBids: (bidderdata) ->
            if bidderdata
              bidderdata.winningBids.length > 0
            else
              false

          winningBidsTotal: (bidderdata) ->
            total = 0
            if bidderdata
              for bid in bidderdata.winningBids
                total += bid.amount
            total

          paymentTotal: (bidderdata) ->
            total = 0
            if bidderdata
              for payment in bidderdata.payments
                total += payment.amount
            total

          pushBidders: () ->
            $http.get('/pushBidders')

          pushItems: () ->
            $http.get('/pushItems')
    ]

    mod.factory 'bidderService', ['$http', 'dataService',
      ($http, dataService) ->
        new class BidderService
          constructor: ->
            @activebidder = undefined

          addBidder: (name) ->
            $http.post('/bidders', { name: name })

          addPayment: (bidder_id, description, amount) ->
            paymentdata = { description: description, amount: amount }
            $http.post('/payments/' + bidder_id, paymentdata)

          setActiveBidder: (id) ->
            filteredBidders = dataService.biddersdata.filter( (bidderdata) -> bidderdata.bidder.id is id )

            if(filteredBidders.length is 1)
              @activebidder = filteredBidders[0]
              console.log "Setting active bidder: " + @activebidder

          clearActiveBidder: ->
            @activebidder = undefined
    ]

    mod.factory 'itemService', ['$http', 'dataService',
      ($http, dataService) ->
        new class ItemService
          constructor: ->
            @activeitem = undefined

          setActiveItem: (id) ->
            filteredItems = dataService.itemsdata.filter( (itemdata) -> itemdata.item.id is id )

            if(filteredItems.length is 1)
              @activeitem = filteredItems[0]

          clearActiveItem: ->
            @activeitem = undefined
    ]

    mod.factory 'bidEntryService', ['$http',
      ($http) ->
        new class BidEntryService
          addWinningBid: (itemid, bidder_id, amount) ->
            biddata = { bidderId: parseInt(bidder_id), amount: parseFloat(amount) }
            $http.post('/items/' + itemid + "/bid", biddata)

          deleteWinningBid: (id) ->
            $http.post('/items/' + id + '/deletebid', {})
    ]
