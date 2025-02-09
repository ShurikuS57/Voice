package voice.bookOverview.views

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import voice.bookOverview.R
import voice.bookOverview.bottomSheet.BottomSheetContent
import voice.bookOverview.bottomSheet.BottomSheetItem
import voice.bookOverview.deleteBook.DeleteBookDialog
import voice.bookOverview.di.BookOverviewComponent
import voice.bookOverview.editTitle.EditBookTitleDialog
import voice.bookOverview.overview.BookOverviewCategory
import voice.bookOverview.overview.BookOverviewViewState
import voice.common.BookId
import voice.common.compose.VoiceTheme
import voice.common.compose.rememberScoped
import voice.common.rootComponentAs
import java.util.UUID

@Composable
fun BookOverviewScreen() {
  val bookComponent = rememberScoped {
    rootComponentAs<BookOverviewComponent.Factory.Provider>()
      .bookOverviewComponentProviderFactory.create()
  }
  val bookOverviewViewModel = bookComponent.bookOverviewViewModel
  val editBookTitleViewModel = bookComponent.editBookTitleViewModel
  val bottomSheetViewModel = bookComponent.bottomSheetViewModel
  val deleteBookViewModel = bookComponent.deleteBookViewModel
  val fileCoverViewModel = bookComponent.fileCoverViewModel
  LaunchedEffect(Unit) {
    bookOverviewViewModel.attach()
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  val viewState by remember(lifecycleOwner, bookOverviewViewModel) {
    bookOverviewViewModel.state().flowWithLifecycle(lifecycleOwner.lifecycle, Lifecycle.State.RESUMED)
  }.collectAsState(initial = BookOverviewViewState.Loading)

  val scope = rememberCoroutineScope()

  val getContentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent(),
    onResult = { uri ->
      if (uri != null) {
        fileCoverViewModel.onImagePicked(uri)
      }
    },
  )

  val bottomSheetState = rememberModalBottomSheetState(ModalBottomSheetValue.Hidden)
  ModalBottomSheetLayout(
    sheetState = bottomSheetState,
    sheetContent = {
      Surface {
        BottomSheetContent(bottomSheetViewModel.state.value) { item ->
          if (item == BottomSheetItem.FileCover) {
            getContentLauncher.launch("image/*")
          }
          scope.launch {
            delay(300)
            bottomSheetState.hide()
            bottomSheetViewModel.onItemClick(item)
          }
        }
      }
    },
  ) {
    BookOverview(
      viewState = viewState,
      onLayoutIconClick = bookOverviewViewModel::toggleGrid,
      onSettingsClick = bookOverviewViewModel::onSettingsClick,
      onBookClick = bookOverviewViewModel::onBookClick,
      onBookLongClick = { bookId ->
        scope.launch {
          bottomSheetViewModel.bookSelected(bookId)
          bottomSheetState.show()
        }
      },
      onBookFolderClick = bookOverviewViewModel::onBookFolderClick,
      onPlayButtonClick = bookOverviewViewModel::playPause,
      onBookMigrationClick = {
        bookOverviewViewModel.onBoomMigrationHelperConfirmClick()
        bookOverviewViewModel.onBookMigrationClick()
      },
      onBoomMigrationHelperConfirmClick = bookOverviewViewModel::onBoomMigrationHelperConfirmClick,
    )
    val deleteBookViewState = deleteBookViewModel.state.value
    if (deleteBookViewState != null) {
      DeleteBookDialog(
        viewState = deleteBookViewState,
        onDismiss = deleteBookViewModel::onDismiss,
        onConfirmDeletion = deleteBookViewModel::onConfirmDeletion,
        onDeleteCheckBoxChecked = deleteBookViewModel::onDeleteCheckBoxChecked,
      )
    }
    val editBookTitleState = editBookTitleViewModel.state.value
    if (editBookTitleState != null) {
      EditBookTitleDialog(
        onDismissEditTitleClick = editBookTitleViewModel::onDismissEditTitle,
        onConfirmEditTitle = editBookTitleViewModel::onConfirmEditTitle,
        viewState = editBookTitleState,
        onUpdateEditTitle = editBookTitleViewModel::onUpdateEditTitle,
      )
    }
  }
}

@Composable
fun BookOverview(
  viewState: BookOverviewViewState,
  onLayoutIconClick: () -> Unit,
  onSettingsClick: () -> Unit,
  onBookClick: (BookId) -> Unit,
  onBookLongClick: (BookId) -> Unit,
  onBookFolderClick: () -> Unit,
  onPlayButtonClick: () -> Unit,
  onBookMigrationClick: () -> Unit,
  onBoomMigrationHelperConfirmClick: () -> Unit,
) {
  val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
  Scaffold(
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      TopAppBar(
        title = {
          Text(text = stringResource(id = R.string.app_name))
        },
        scrollBehavior = scrollBehavior,
        actions = {
          if (viewState.showMigrateIcon) {
            MigrateIcon(
              onClick = onBookMigrationClick,
              withHint = viewState.showMigrateHint,
              onHintClick = onBoomMigrationHelperConfirmClick,
            )
          }
          BookFolderIcon(withHint = viewState.showAddBookHint, onClick = onBookFolderClick)

          val layoutIcon = viewState.layoutIcon
          if (layoutIcon != null) {
            LayoutIcon(layoutIcon, onLayoutIconClick)
          }
          SettingsIcon(onSettingsClick)
        },
      )
    },
    floatingActionButton = {
      if (viewState.playButtonState != null) {
        PlayButton(
          playing = viewState.playButtonState == BookOverviewViewState.PlayButtonState.Playing,
          onClick = onPlayButtonClick,
        )
      }
    },
  ) { contentPadding ->
    when (viewState) {
      is BookOverviewViewState.Content -> {
        when (viewState.layoutMode) {
          BookOverviewViewState.Content.LayoutMode.List -> {
            ListBooks(
              books = viewState.books,
              onBookClick = onBookClick,
              onBookLongClick = onBookLongClick,
              contentPadding = contentPadding,
            )
          }
          BookOverviewViewState.Content.LayoutMode.Grid -> {
            GridBooks(
              books = viewState.books,
              onBookClick = onBookClick,
              onBookLongClick = onBookLongClick,
              contentPadding = contentPadding,
            )
          }
        }
      }
      BookOverviewViewState.Loading -> {
        Box(
          Modifier
            .fillMaxSize()
            .padding(contentPadding),
        ) {
          CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
      }
    }
  }
}

@Preview
@Composable
fun BookOverviewPreview(
  @PreviewParameter(BookOverviewPreviewParameterProvider::class)
  viewState: BookOverviewViewState,
) {
  VoiceTheme {
    BookOverview(
      viewState = viewState,
      onLayoutIconClick = {},
      onSettingsClick = {},
      onBookClick = {},
      onBookLongClick = {},
      onBookFolderClick = {},
      onPlayButtonClick = {},
      onBookMigrationClick = {},
    ) {}
  }
}

internal class BookOverviewPreviewParameterProvider : PreviewParameterProvider<BookOverviewViewState> {

  fun book(): BookOverviewViewState.Content.BookViewState {
    return BookOverviewViewState.Content.BookViewState(
      name = "Book",
      author = "Author",
      cover = null,
      progress = 0.8F,
      id = BookId(UUID.randomUUID().toString()),
      remainingTime = "01:04",
    )
  }

  override val values = sequenceOf(
    BookOverviewViewState.Loading,
    BookOverviewViewState.Content(
      layoutIcon = BookOverviewViewState.Content.LayoutIcon.List,
      books = mapOf(
        BookOverviewCategory.CURRENT to buildList { repeat(10) { add(book()) } },
        BookOverviewCategory.FINISHED to listOf(book(), book()),
      ),
      layoutMode = BookOverviewViewState.Content.LayoutMode.List,
      playButtonState = BookOverviewViewState.PlayButtonState.Paused,
      showAddBookHint = false,
      showMigrateHint = false,
      showMigrateIcon = true,
    ),
  )
}
