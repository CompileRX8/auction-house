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

    mod.factory 'statusService', ['$http', '$interval',
      ($http, $interval) ->
        new class StatusService
          constructor: () ->
            @message = null
            @messageQueue = []
            @_intervalsMsgShown = 0

            setStatus = () =>
              if @message
                if @_intervalsMsgShown >= 10
                  @_intervalsMsgShown = 0
                  @message = null
                else
                  @_intervalsMsgShown += 1
              else
                if @messageQueue.length > 0
                  @message = @messageQueue.pop()

            @_updateInterval = $interval(setStatus, 500)

            console.log "Created StatusService"

          _addMessage: (text, status) ->
            @messageQueue.push({text: text, status: status})

          httpPost: (url, data) ->
            httpSuccess = (data, status, headers, config) =>
              @_addMessage(data, "alert alert-success")
              console.log "HTTP Success: data = " + data + " status = " + status

            httpError = (data, status, headers, config) =>
              @_addMessage(data, "alert alert-danger")
              console.log "HTTP Error: data = " + data + " status = " + status

            $http.post(url, data).success(httpSuccess).error(httpError)
    ]

    mod.factory 'dataService', ['$http', '$interval',
      ($http, $interval) ->
        new class DataService
          @count: 0

          constructor: () ->
            @_myid = ++DataService.count

            @biddersdata = {}
            @itemsdata = {}
            @winningbids = {}
            @totalBids = 0
            @bidderUpdateCallback = null
            @itemUpdateCallback = null
            @winningBidUpdateCallback = null

            @_lastUpdate = Date.now()

            updateBidders = (msg) =>
              @_lastUpdate = Date.now()
              console.log "Received new biddersdata: " + msg
              @biddersdata = @_parseMsg(msg)
              @_updateTotalBids(@biddersdata)
              if @bidderUpdateCallback
                @bidderUpdateCallback()
              if @winningBidUpdateCallback
                @winningBidUpdateCallback()

            @_biddersFeed = new EventSource("/biddersFeed")
            @_biddersFeed.addEventListener("message", updateBidders, false)

            updateItems = (msg) =>
              @_lastUpdate = Date.now()
              console.log "Received new itemsdata: " + msg
              @itemsdata = @_parseMsg(msg)
              for itemdata in @itemsdata
                item = itemdata.item
                matches = item.itemNumber.match(/\d+/)
                if matches && matches.length > 0
                  item.sortField = ("0000000000" + item.itemNumber).substr(matches[0].length)
                else
                  item.sortField = item.itemNumber
              @_updateTotalBids(@itemsdata)
              if @itemUpdateCallback
                @itemUpdateCallback()
              if @winningBidUpdateCallback
                @winningBidUpdateCallback()

            @_itemsFeed = new EventSource("/itemsFeed")
            @_itemsFeed.addEventListener("message", updateItems, false)

            callPush = () =>
              now = Date.now()
              timeSinceLastUpdate = now - @_lastUpdate
              if timeSinceLastUpdate > 5000
                @pushBidders()
                @pushItems()

            @_updateInterval = $interval(callPush, 1000)

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
            parsedData

          setBidderUpdateCallback: (callback) ->
            @bidderUpdateCallback = callback

          clearBidderUpdateCallback: () ->
            @bidderUpdateCallback = null

          setItemUpdateCallback: (callback) ->
            @itemUpdateCallback = callback

          clearItemUpdateCallback: () ->
            @itemUpdateCallback = null

          setWinningBidUpdateCallback: (callback) ->
            @winningBidUpdateCallback = callback

          clearWinningBidUpdateCallback: () ->
            @winningBidUpdateCallback = null

          hasWinningBids: (bidderdata) ->
            if bidderdata
              bidderdata.winningBids.length > 0
            else
              false

          hasPayments: (bidderdata) ->
            if bidderdata
              bidderdata.payments.length > 0
            else
              false

          winningBidsTotal: (bidderdata) ->
            total = 0
            if bidderdata
              for bid in bidderdata.winningBids
                total += bid.amount
            total

          estValueTotal: (bidderdata) ->
            total = 0
            if bidderdata
              for bid in bidderdata.winningBids
                total += bid.item.estvalue
            total

          paymentTotal: (bidderdata) ->
            total = 0
            if bidderdata
              for payment in bidderdata.payments
                total += payment.amount
            total

          totalsStyle: (bidderdata) ->
            winningBidsTotal = @winningBidsTotal(bidderdata)
            paymentsTotal = @paymentTotal(bidderdata)
            totalsStyle = ""

            if winningBidsTotal > 0
              if paymentsTotal >= winningBidsTotal
                totalsStyle = "bg-success"
              else if paymentsTotal <= 0
                totalsStyle = "bg-danger"
              else if paymentsTotal > 0
                totalsStyle = "bg-warning"

            totalsStyle

          pushBidders: () ->
            $http.get('/pushBidders')

          pushItems: () ->
            $http.get('/pushItems')
    ]

    mod.factory 'bidderService', ['dataService', 'statusService',
      (dataService, statusService) ->
        new class BidderService
          constructor: ->
            @activebidder = undefined

            updateActiveBidder = () =>
              if @activebidder
                @setActiveBidder(@activebidder.bidder.id)

            dataService.setBidderUpdateCallback(updateActiveBidder)

          addBidder: (name) ->
            statusService.httpPost('/bidders', { name: name })

          editBidder: (bidder_id, name) ->
            statusService.httpPost('/bidders/' + bidder_id + '/edit', { name: name })

          deleteBidder: (bidder_id) ->
            statusService.httpPost('/bidders/' + bidder_id + '/delete', {})

          addPayment: (bidder_id, description, amount) ->
            paymentdata = { description: description, amount: amount }
            statusService.httpPost('/payments/' + bidder_id, paymentdata)

          setActiveBidder: (id) ->
            filteredBidders = dataService.biddersdata.filter( (bidderdata) -> bidderdata.bidder.id is id )

            if(filteredBidders.length is 1)
              @activebidder = filteredBidders[0]
              console.log "Setting active bidder: " + @activebidder
            else
              @clearActiveBidder()

          clearActiveBidder: ->
            @activebidder = undefined
    ]

    mod.factory 'itemService', ['dataService', 'statusService',
      (dataService, statusService) ->
        new class ItemService
          constructor: ->
            @activeitem = undefined

            updateActiveItem = () =>
              if @activeitem
                @setActiveItem(@activeitem.item.id)

            dataService.setItemUpdateCallback(updateActiveItem)

          addItem: (item_num, category, donor, description, min_bid, est_value) ->
            itemdata = { item_num: item_num, category: category, donor: donor, description: description, min_bid: parseFloat(min_bid), est_value: parseFloat(est_value) }
            statusService.httpPost('/items', itemdata)

          editItem: (item_id, item_num, category, donor, description, min_bid, est_value) ->
            itemdata = { item_num: item_num, category: category, donor: donor, description: description, min_bid: parseFloat(min_bid), est_value: parseFloat(est_value) }
            statusService.httpPost('/items/' + item_id + '/edit', itemdata)

          deleteItem: (item_id) ->
            statusService.httpPost('/items/' + item_id + '/delete', {})

          setActiveItem: (id) ->
            filteredItems = dataService.itemsdata.filter( (itemdata) -> itemdata.item.id is id )

            if(filteredItems.length is 1)
              @activeitem = filteredItems[0]
            else
              @clearActiveItem()

          clearActiveItem: ->
            @activeitem = undefined
    ]

    mod.factory 'bidEntryService', ['dataService', 'statusService',
      (dataService, statusService) ->
        new class BidEntryService
          constructor: ->
            @activebid = undefined

            updateActiveBid = () =>
              if @activebid
                @setActiveBid(@activebid.id)

            dataService.setWinningBidUpdateCallback(updateActiveBid)

          addWinningBid: (itemid, bidder_id, amount) ->
            biddata = { bidderId: parseInt(bidder_id), amount: parseFloat(amount) }
            statusService.httpPost('/items/' + itemid + "/bid", biddata)

          editWinningBid: (bid_id, item_id, bidder_id, amount) ->
            biddata = { bidderId: parseInt(bidder_id), itemId: parseInt(item_id), amount: parseFloat(amount) }
            statusService.httpPost('/items/' + bid_id + '/editbid', biddata)

          deleteWinningBid: (id) ->
            statusService.httpPost('/items/' + id + '/deletebid', {})

          setActiveBid: (id) ->
            filteredBids = dataService.winningbids.filter( (winningbid) -> winningbid.id is id )

            if(filteredBids.length is 1)
              @activebid = filteredBids[0]
            else
              @clearActiveBid()

          clearActiveBid: ->
            @activebid = undefined
    ]

    mod
