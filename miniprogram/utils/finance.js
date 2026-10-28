const { request } = require('./api');
async function selectedBook() {
  const books = await request('/api/finance/books');
  const saved = Number(wx.getStorageSync('selectedBookId'));
  const book = books.find(item => item.id === saved) || books[0];
  if (book) wx.setStorageSync('selectedBookId', book.id);
  return { books, book };
}
function chooseBook(id) { wx.setStorageSync('selectedBookId', id); }
function path(bookId, suffix) { return `/api/finance/books/${bookId}${suffix}`; }
const pad = n => String(n).padStart(2, '0');
function today() { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; }
function currentTime() { const d = new Date(); return `${pad(d.getHours())}:${pad(d.getMinutes())}`; }
module.exports = { selectedBook, chooseBook, path, today, currentTime };
