Component({
  data: { selected: 0 },
  methods: {
    onTap(event) {
      const index = Number(event.currentTarget.dataset.index);
      const paths = ['/pages/ledger/index', '/pages/add/index', '/pages/me/index'];
      wx.switchTab({ url: paths[index] });
    }
  }
});
