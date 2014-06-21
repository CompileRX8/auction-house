define ['angular'],
  (angular) ->
    mod = angular.module 'AuctionHouse.controllers', ['AuctionHouse.services']

    mod.controller 'HomeCtrl', ['$scope',
      ($scope) ->
        $scope.activebidder = undefined
        $scope.activeitem = undefined

        $scope.hasWinningBids = (bidderdata) ->
          bidderdata.winningBids.length > 0

        $scope.winningBidsTotal = (bidderdata) ->
          total = 0
          for bid in bidderdata.winningBids
            total += bid.amount
          total

        $scope.paymentTotal = (bidderdata) ->
          total = 0
          for payment in bidderdata.payments
            total += payment.amount
          total

        $scope.setActiveBidder = (id) ->
          filteredBidders = $scope.biddersdata.filter( (bidderdata) -> bidderdata.bidder.id is id )

          if(filteredBidders.length is 1)
            $scope.activebidder = filteredBidders[0]

        $scope.clearActiveBidder = ->
          $scope.activebidder = undefined

        $scope.setActiveItem = (id) ->
          filteredItems = $scope.itemsdata.filter( (itemdata) -> itemdata.item.id is id )

          if(filteredItems.length is 1)
            $scope.activeitem = filteredItems[0]

        $scope.clearActiveItem = ->
          $scope.activeitem = undefined

        $scope.updateTotalBids = ->
          $scope.totalBids = 0
          for winningbid in $scope.winningbids
            $scope.totalBids += winningbid.amount

        $scope.updateBidders = (msg) ->
          $scope.$apply ->
            parsedData = JSON.parse(msg.data)
            $scope.biddersdata = parsedData

            $scope.winningbids = []
            for bidderdata in $scope.biddersdata
              for winningbid in bidderdata.winningBids
                $scope.winningbids.push(winningbid)

            $scope.updateTotalBids()

        $scope.updateItems = (msg) ->
          $scope.$apply ->
            parsedData = JSON.parse(msg.data)
            $scope.itemsdata = parsedData

            $scope.winningbids = []
            for itemdata in $scope.itemsdata
              for winningbid in itemdata.winningBids
                $scope.winningbids.push(winningbid)

            $scope.updateTotalBids()

        $scope.listen = ->
          $scope.biddersFeed = new EventSource("/biddersFeed")
          $scope.biddersFeed.addEventListener("message", $scope.updateBidders, false)
          $scope.itemsFeed = new EventSource("/itemsFeed")
          $scope.itemsFeed.addEventListener("message", $scope.updateItems, false)

        $scope.listen()
    ]

    mod.controller 'BiddersCtrl', ['$scope', 'bidderService',
      ($scope, bidderService) ->

        $scope.payment = {}
        $scope.bidder_name = ''

        $scope.addBidder = ->
          bidderService.addBidder($scope.bidder_name)
          $scope.bidder_name = ''

        $scope.addPayment = ->
          bidderService.addPayment($scope.activebidder.bidder.id, $scope.payment.description, parseFloat($scope.payment.amount))
          $scope.payment = {}
    ]

    mod.controller 'ItemsCtrl', ->

    mod.controller 'BidEntryCtrl', ['$scope', 'bidEntryService',
      ($scope, bidEntryService) ->
        $scope.winningbid = {}

        $scope.filtered_items = ->
          itemnum = $scope.winningbid.item_num
          $scope.itemsdata.filter( (itemdata) -> itemdata.item.itemNumber is itemnum )

        $scope.item_num_change = ->
          filteredItems = $scope.filtered_items()

          $scope.winningbid.item_desc = if(filteredItems.length is 1)
            filteredItems[0].item.description
          else
            '...'

        $scope.bidder_id_change = ->
          bidderid = parseInt($scope.winningbid.bidder_id)
          filteredBidders = $scope.biddersdata.filter( (bidderdata) -> bidderdata.bidder.id is bidderid )

          $scope.winningbid.bidder_name = if(filteredBidders.length is 1)
            filteredBidders[0].bidder.name
          else
            '...'

        $scope.addWinningBid = ->
          filteredItems = $scope.filtered_items()

          if(filteredItems.length is 1)
            itemid = filteredItems[0].item.id
            bidEntryService.addWinningBid(itemid, $scope.winningbid.bidder_id, $scope.winningbid.amount)
            $scope.winningbid = {}

        $scope.deleteWinningBid = (id) ->
          bidEntryService.deleteWinningBid(id)

        $scope.setActiveBid = (id) ->
          $scope.winningbid.id = id

        $scope.clearActiveBid = ->
          $scope.winningbid.id = 0
    ]

    mod.controller 'HeaderCtrl', ['$scope', 'userService',
      ($scope, userService) ->
        $scope.user = {}
        $scope.credentials = {}

        $scope.login = ->
          userService.loginUser($scope.credentials).then (user) ->
            $scope.user = user

        $scope.logout = ->
          userService.logoutUser($scope.user.token).then (data) ->
            $scope.user = undefined
            $scope.credentials = {}
    ]

    mod.controller 'FooterCtrl', ->

    mod.controller 'ReconCtrl', ->
