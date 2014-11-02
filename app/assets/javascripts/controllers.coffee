define ['angular'],
  (angular) ->
    mod = angular.module 'AuctionHouse.controllers', ['AuctionHouse.services']

    mod.controller 'HomeCtrl', ['$scope', 'dataService',
      ($scope, dataService) ->
        console.log "Created HomeCtrl"
        $scope.dataService = dataService

        dataService.pushBidders()
        dataService.pushItems()
    ]

    mod.controller 'BiddersCtrl', ['$scope', 'bidderService', 'dataService',
      ($scope, bidderService, dataService) ->
        console.log "Created BiddersCtrl"
        $scope.payment = {}
        $scope.bidder_name = ''
        $scope.edit_bidder_name = ''

        $scope.dataService = dataService
        $scope.bidderService = bidderService

        $scope.addBidder = ->
          bidderService.addBidder($scope.bidder_name)
          $scope.bidder_name = ''

        $scope.deleteBidder = ->
          bidderService.deleteBidder(bidderService.activebidder.bidder.id)
          $scope.bidderCleared()

        $scope.editBidder = ->
          bidderService.editBidder(bidderService.activebidder.bidder.id, $scope.edit_bidder_name)

        $scope.addPayment = ->
          bidderService.addPayment(bidderService.activebidder.bidder.id, $scope.payment.description, parseFloat($scope.payment.amount))
          $scope.payment = {}

        $scope.bidderClicked = (id) ->
          bidderService.setActiveBidder(id)
          $scope.edit_bidder_name = bidderService.activebidder.bidder.name

        $scope.bidderCleared = ->
          bidderService.clearActiveBidder()
          $scope.edit_bidder_name = ''

        dataService.pushBidders()

        if(bidderService.activebidder)
          $scope.bidderClicked($scope.bidderService.activebidder.bidder.id)
    ]

    mod.controller 'ItemsCtrl', ['$scope', 'itemService', 'dataService',
      ($scope, itemService, dataService) ->
        console.log "Created ItemsCtrl"
        $scope.item = {}
        $scope.edititem = {}

        $scope.dataService = dataService
        $scope.itemService = itemService

        $scope.addItem = ->
          category = $scope.item.category or ''
          donor = $scope.item.donor or ''
          itemService.addItem($scope.item.item_num, category, donor, $scope.item.description, $scope.item.min_bid)
          $scope.item = {}

        $scope.editItem = ->
          category = $scope.edititem.category or ''
          donor = $scope.edititem.donor or ''
          itemService.editItem(itemService.activeitem.item.id, $scope.edititem.item_num, category, donor, $scope.edititem.description, $scope.edititem.min_bid)

        $scope.deleteItem = ->
          itemService.deleteItem(itemService.activeitem.item.id)
          $scope.itemCleared()

        $scope.itemClicked = (id) ->
          itemService.setActiveItem(id)
          $scope.edititem.item_num = itemService.activeitem.item.itemNumber
          $scope.edititem.category = itemService.activeitem.item.category
          $scope.edititem.donor = itemService.activeitem.item.donor
          $scope.edititem.description = itemService.activeitem.item.description
          $scope.edititem.min_bid = itemService.activeitem.item.minbid

        $scope.itemCleared = ->
          itemService.clearActiveItem()
          $scope.edititem = {}

        dataService.pushItems()

        if(itemService.activeitem)
          $scope.itemClicked(itemService.activeitem.item.id)
    ]

    mod.controller 'BidEntryCtrl', ['$scope', 'bidEntryService', 'dataService',
      ($scope, bidEntryService, dataService) ->
        console.log "Created BidEntryCtrl"
        $scope.dataService = dataService
        $scope.bidEntryService = bidEntryService

        $scope.newbid = {}
        $scope.editbid = {}

        $scope._filtered_items = (bidobj) ->
          itemnum = bidobj.itemNumber
          dataService.itemsdata.filter( (itemdata) -> itemdata.item.itemNumber is itemnum )

        $scope.item_num_change = (bidobj) ->
          filteredItems = $scope._filtered_items(bidobj)

          bidobj.description = if(filteredItems.length is 1)
            filteredItems[0].item.description
          else
            '...'

        $scope.bidder_id_change = (bidobj) ->
          bidderid = parseInt(bidobj.bidderId)
          filteredBidders = dataService.biddersdata.filter( (bidderdata) -> bidderdata.bidder.id is bidderid )

          bidobj.bidderName = if(filteredBidders.length is 1)
            filteredBidders[0].bidder.name
          else
            '...'

        $scope.addWinningBid = ->
          filteredItems = $scope._filtered_items($scope.newbid)

          if(filteredItems.length is 1)
            itemid = filteredItems[0].item.id
            bidEntryService.addWinningBid(itemid, $scope.newbid.bidderId, $scope.newbid.amount)
            $scope.newbid = {}

        $scope.editWinningBid = ->
          filteredItems = $scope._filtered_items($scope.editbid)

          if(filteredItems.length is 1)
            itemid = filteredItems[0].item.id
            bidEntryService.editWinningBid(bidEntryService.activebid.id, itemid, $scope.editbid.bidderId, $scope.editbid.amount)
            $scope.bidCleared()

        $scope.deleteWinningBid = ->
          if bidEntryService.activebid
            bidEntryService.deleteWinningBid(bidEntryService.activebid.id)
            $scope.bidCleared()
          else
            console.log("deleteWinningBid called without activebid")

        $scope.bidClicked = (id) ->
          if bidEntryService.activebid && bidEntryService.activebid.id == id
            $scope.bidCleared()
          else
            bidEntryService.setActiveBid(id)
            bid = bidEntryService.activebid
            $scope.editbid = {
              bidderId: bid.bidder.id,
              bidderName: bid.bidder.name,
              itemId: bid.item.id,
              itemNumber: bid.item.itemNumber,
              description: bid.item.description,
              amount: bid.amount
            }

        $scope.bidCleared = ->
          bidEntryService.clearActiveBid()
          $scope.editbid = {}

        dataService.pushBidders()
        dataService.pushItems()
    ]

    mod.controller 'HeaderCtrl', ['$scope', 'userService',
      ($scope, userService) ->
        console.log "Created HeaderCtrl"
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

    mod.controller 'FooterCtrl', ['$scope', 'statusService',
      ($scope, statusService) ->
        $scope.statusService = statusService
    ]

    mod.controller 'ReconCtrl', ['$scope', 'dataService',
      ($scope, dataService) ->
        $scope.dataService = dataService
    ]
