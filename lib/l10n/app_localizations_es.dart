// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Spanish Castilian (`es`).
class AppLocalizationsEs extends AppLocalizations {
  AppLocalizationsEs([String locale = 'es']) : super(locale);

  @override
  String get servicesTitle => 'Servicios de desarrollo';

  @override
  String get servicesCopy => 'Copiar comando';

  @override
  String get servicesSubtitle =>
      'Comandos del proyecto, registros y enlaces de vista previa';

  @override
  String get servicesIntro =>
      'Reúne los comandos de desarrollo de tu proyecto y sus enlaces de vista previa. Guardar un servicio no lo inicia.';

  @override
  String get servicesAdd => 'Registrar servicio';

  @override
  String get servicesName => 'Nombre del servicio';

  @override
  String get servicesCommand => 'Comando de desarrollo';

  @override
  String get servicesCommandHint =>
      'Usa un comando en primer plano, como npm run dev. Los comandos en segundo plano o desacoplados no se pueden seguir.';

  @override
  String get servicesUrl => 'URL de vista previa (opcional)';

  @override
  String get servicesUrlHint =>
      'Usa una dirección que este teléfono pueda alcanzar. localhost apunta a este teléfono. No se exponen ni se reenvían puertos por ti.';

  @override
  String get servicesSave => 'Guardar servicio';

  @override
  String get servicesUnavailable =>
      'Este servidor no puede iniciar ni seguir comandos de desarrollo. Puedes guardar comandos y revisar sus enlaces de vista previa aquí.';

  @override
  String get servicesScopeChanged =>
      'El servidor o el proyecto cambiaron. Reabre Servicios de desarrollo desde el proyecto correspondiente.';

  @override
  String get servicesNotStarted => 'No iniciado';

  @override
  String get servicesRunning => 'Comando en ejecución';

  @override
  String get servicesStopped => 'Detenido';

  @override
  String get servicesUnknown => 'Estado desconocido';

  @override
  String get servicesStatusHint =>
      'El estado del comando no confirma que tu app esté lista o accesible.';

  @override
  String get servicesStart => 'Iniciar';

  @override
  String get servicesStop => 'Detener';

  @override
  String get servicesRestart => 'Reiniciar';

  @override
  String get servicesLogs => 'Registros';

  @override
  String get servicesVisit => 'Visitar';

  @override
  String get servicesRemove => 'Quitar la configuración';

  @override
  String get servicesStopHint =>
      '¿Detener el comando registrado de este servicio? El servidor también elimina sus logs conservados. No afecta a otros comandos.';

  @override
  String get servicesRestartHint =>
      '¿Detener este comando registrado, eliminar su log del servidor y volver a iniciar el comando guardado?';

  @override
  String get servicesForget => 'Olvidar la última ejecución';

  @override
  String get servicesForgetHint =>
      '¿Borrar el registro local de la ejecución? Esto no detiene ningún proceso del servidor. Al iniciar de nuevo puede duplicarse si el comando anterior sigue en ejecución.';

  @override
  String get servicesUnknownHint =>
      'No se pudo confirmar la última ejecución. Actualiza para conciliarla antes de iniciar de nuevo.';

  @override
  String get servicesLogEmpty => 'Aún no hay salida capturada.';

  @override
  String get servicesWorking => 'Actualizando el servicio…';

  @override
  String servicesExit(int code) {
    return 'Código de salida registrado: $code';
  }

  @override
  String get servicesRefresh => 'Actualizar el estado';

  @override
  String get isolatedTaskScopeChanged =>
      'El servidor o el proyecto cambiaron mientras esto estaba abierto. Ciérralo y empieza de nuevo desde el proyecto que quieres.';

  @override
  String get appTitle => 'OpenCode Mobile';

  @override
  String get libraryModelsAgentsTitle => 'Modelos y agentes';

  @override
  String get libraryProvidersTitle => 'Proveedores';

  @override
  String get libraryMcpTitle => 'MCP';

  @override
  String get libraryCommandsToolsTitle => 'Comandos y herramientas';

  @override
  String get libraryTerminalTitle => 'Terminal';

  @override
  String get librarySettingsTitle => 'Ajustes';

  @override
  String aboutBuildVersion(String version, String buildNumber) {
    return 'OpenCode Mobile $version+$buildNumber';
  }

  @override
  String get aboutSigningCertificate => 'Certificado de firma SHA-256';

  @override
  String get modelSwitchSession => 'Cambiar de modelo en esta conversación';

  @override
  String get modelNextFavorite => 'Siguiente modelo favorito';

  @override
  String get modelChooseTitle => 'Elegir un modelo';

  @override
  String get modelTitleCompact => 'Modelos';

  @override
  String get modelSearchHint => 'Buscar modelos';

  @override
  String get modelAll => 'Todos los modelos';

  @override
  String get modelFavorites => 'Favoritos';

  @override
  String get modelRecent => 'Recientes';

  @override
  String get modelOptions => 'Opciones';

  @override
  String get modelThinkingMode => 'Modo de razonamiento';

  @override
  String get modelServerDefault => 'Predeterminado del servidor';

  @override
  String get modelSelectionSaving =>
      'Guardando la selección de la conversación…';

  @override
  String get modelUnavailableSelection =>
      'El modelo de la conversación no está disponible en este catálogo. Actualiza los modelos o elige otro.';

  @override
  String get modelScopeChanged =>
      'El servidor cambió. Vuelve a abrir el selector de modelos para continuar.';

  @override
  String get commonClearSearch => 'Borrar búsqueda';

  @override
  String get workTitle => 'Tareas';

  @override
  String get workRefresh => 'Actualizar';

  @override
  String get workRetry => 'Reintentar';

  @override
  String get workCancel => 'Cancelar';

  @override
  String get workRunning => 'En ejecución';

  @override
  String get workFinished => 'Terminado';

  @override
  String get workTimedOut => 'Tiempo agotado';

  @override
  String get workStopped => 'Detenido';

  @override
  String get workUnknown => 'Estado no disponible';

  @override
  String get workOutput => 'Salida del comando';

  @override
  String get workNoOutput => 'Esperando salida…';

  @override
  String get workNoFinalOutput => 'Este comando no produjo salida.';

  @override
  String get workMoreOutput => 'Cargar más salida';

  @override
  String get workTrimmed =>
      'Se muestra la salida más reciente. El texto anterior se recortó.';

  @override
  String get workStop => 'Detener comando';

  @override
  String get workStopTitle => '¿Detener este comando?';

  @override
  String get workStopDescription =>
      'Esto detiene el comando y elimina de su servidor la salida guardada. El texto ya cargado aquí sigue visible hasta que lo cierres.';

  @override
  String get workTimeout => 'Cambiar límite de tiempo';

  @override
  String get workTimeoutDescription => 'El nuevo límite empieza ahora.';

  @override
  String get workTimeoutOneMinute => '1 minuto';

  @override
  String get workTimeoutFiveMinutes => '5 minutos';

  @override
  String get workTimeoutFifteenMinutes => '15 minutos';

  @override
  String get workTimeoutOneHour => '1 hora';

  @override
  String get workTimeoutNone => 'Sin límite';

  @override
  String get workUnavailable =>
      'Este comando ya no está disponible. Puede que lo hayan quitado o cancelado al reiniciarse el servidor.';

  @override
  String get workRestarted =>
      'El servidor se reinició y este comando ya no está disponible. Abajo se muestra su salida cargada.';

  @override
  String get workDisconnected =>
      'Reconectando. La salida se actualizará cuando el servidor esté disponible.';

  @override
  String get workContextChanged =>
      'Cambió el servidor o el proyecto. Cierra esta vista y vuelve a abrir Trabajo en curso.';

  @override
  String workCount(int count) {
    return 'Tareas · $count en curso';
  }

  @override
  String workExitCode(int code) {
    return 'Código de salida $code';
  }

  @override
  String workStatusElapsed(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String get composerClearTextTitle => 'Borrar el texto del borrador';

  @override
  String get composerClearTextSubtitle =>
      'Conserva los adjuntos · Se puede deshacer';

  @override
  String get composerDraftCleared => 'Texto del borrador borrado';

  @override
  String get composerReuseTitle => 'Reutilizar un mensaje';

  @override
  String get queueSaveFailed =>
      'No se pudo guardar el borrador en cola en este dispositivo. Tu texto sigue aquí. Revisa el espacio disponible e inténtalo de nuevo.';

  @override
  String get fileSave => 'Guardar';

  @override
  String get fileReload => 'Actualizar';

  @override
  String get queueRemoveFailed =>
      'No se pudo quitar este borrador del almacenamiento del dispositivo. Sigue en cola. Revisa el espacio disponible e inténtalo de nuevo.';

  @override
  String get composerReuseSubtitle =>
      'Reutiliza texto de esta conversación y de envíos recientes';

  @override
  String get composerReuseSearch => 'Buscar mensajes recientes';

  @override
  String get composerReuseEmpty => 'No hay mensajes coincidentes';

  @override
  String get backgroundWorkTitle => 'Pasar el trabajo en curso a segundo plano';

  @override
  String get backgroundWorkShortcut =>
      'Continúa este trabajo mientras usas la conversación · Ctrl+B';

  @override
  String get backgroundWorkNoop =>
      'No hay subagentes en primer plano que pasar a segundo plano.';

  @override
  String get backgroundWorkPromoted =>
      'Los subagentes continúan en segundo plano.';

  @override
  String get libraryNoModel => 'Sin modelo seleccionado';

  @override
  String get chatAttachmentUnsupported =>
      'Se pueden adjuntar imágenes, PDF, archivos de texto y archivos de Excel o Word (.xlsx, .docx).';

  @override
  String get chatCopyCompleteReply => 'Copiar la respuesta completa';

  @override
  String get chatCopyReplySoFar => 'Copiar la respuesta hasta ahora';

  @override
  String commandRunTitle(String command) {
    return 'Ejecutar /$command';
  }

  @override
  String get commandDestination => 'Conversación';

  @override
  String get commandNewChat => 'Nueva conversación';

  @override
  String get commandUntitledChat => 'Conversación sin título';

  @override
  String get commandArguments => 'Argumentos (opcional)';

  @override
  String get commandRun => 'Ejecutar';

  @override
  String get commandRunning => 'Iniciando…';

  @override
  String get commandLocationChanged =>
      'El servidor o el proyecto cambiaron. Cierra este diálogo y abre el comando de nuevo.';

  @override
  String get refreshFailed => 'No se pudo actualizar';

  @override
  String get refreshRetry => 'Intentar de nuevo';

  @override
  String get globalSessionsLoadMore => 'Cargar más conversaciones';

  @override
  String get globalSessionsRefreshFailed =>
      'No se pudieron actualizar las conversaciones.';

  @override
  String get workspaceRetryProjects => 'Reintentar';

  @override
  String get historyLoadOlder => 'Cargar mensajes anteriores';

  @override
  String get historyReload => 'Actualizar el historial reciente';

  @override
  String get historyCursorExpired =>
      'El historial anterior cambió o caducó. Actualiza el historial reciente para continuar.';

  @override
  String get historyLoadedOnly =>
      'Solo se incluyen los mensajes cargados. Carga el historial anterior para incluir más.';

  @override
  String get historyLoadedTotals => 'Consumo e historial cargado';

  @override
  String get historyCopyLoadedReply => 'Copiar la respuesta cargada';

  @override
  String get historyLoadedMessages => 'Mensajes cargados';

  @override
  String get historyLoadedCost => 'Costo de los mensajes cargados';

  @override
  String get historyServerTotalsNote =>
      'Las filas marcadas como informadas por el servidor cubren toda la conversación. Los conteos de mensajes y otros cálculos cubren el historial cargado.';

  @override
  String get sessionsReload => 'Actualizar conversaciones recientes';

  @override
  String get revertReviewChanged =>
      'Esta conversación o su reverso preparado cambiaron. Revisa el estado más reciente antes de continuar.';

  @override
  String get revertReviewLatest => 'Revisar el estado más reciente';

  @override
  String get revertBusy =>
      'Espera a que termine la acción actual de la conversación.';

  @override
  String get revertClearAction => 'Descartar el reverso preparado';

  @override
  String get revertPreviewUnavailable =>
      'El servidor no ofreció una vista previa del archivo. Esto no establece si los archivos cambiaron.';

  @override
  String get revertStaged => 'Reversión preparada';

  @override
  String get revertReview => 'Revisar';

  @override
  String get revertFromHere => 'Deshacer desde aquí';

  @override
  String get revertUndoDescription =>
      'Deshacer el último mensaje y todo lo que vino después';

  @override
  String get revertClearShortDescription =>
      'Revisar y descartar el reverso preparado';

  @override
  String get revertPromptUnavailable => 'No se pudo cargar el mensaje límite.';

  @override
  String get revertPromptLoading => 'Cargando el mensaje límite…';

  @override
  String get revertAttachmentPrompt => 'Mensaje con solo adjuntos';

  @override
  String get revertResolveBeforeSending =>
      'Revisa la reversión preparada y bórrala o hazla permanente antes de enviar. Tu borrador se conserva.';

  @override
  String get sessionNoteTitle => 'Nota para el agente';

  @override
  String get sessionNoteDescription =>
      'Guarda una instrucción breve para esta conversación. Guardarla o borrarla surte efecto en el siguiente paso del agente y aparece en la transcripción en ese momento. No inicia una ejecución.';

  @override
  String get sessionNoteHint =>
      'Por ejemplo: Mantén las explicaciones breves y corre las verificaciones pertinentes antes de terminar.';

  @override
  String get sessionNoteSave => 'Guardar nota';

  @override
  String get sessionNoteRemove => 'Borrar la nota guardada';

  @override
  String get sessionNoteSaved => 'Nota guardada';

  @override
  String get sessionNoteRemoved => 'Nota eliminada';

  @override
  String get sessionNotePending => 'Se aplica en el siguiente paso del agente.';

  @override
  String get sessionInstructionsUpdated => 'Instrucciones actualizadas';

  @override
  String get sessionInstructionsApplied =>
      'Se actualizaron las instrucciones de conversación del agente para este paso.';

  @override
  String get sessionNoteUnsupported =>
      'Este servidor no admite notas de conversación.';

  @override
  String get sessionNoteAuthorization =>
      'Revisa la contraseña y los permisos de este servidor y reintenta. Tu borrador se conserva.';

  @override
  String get sessionNoteChanged =>
      'La conversación o sus instrucciones cambiaron. Actualiza la nota guardada antes de guardar de nuevo. Tu borrador se conserva.';

  @override
  String get sessionNoteInvalid =>
      'La nota guardada tiene un formato que este editor no puede cambiar con seguridad.';

  @override
  String get sessionNoteTooLarge =>
      'Acerca la nota al límite de tamaño del servidor.';

  @override
  String get sessionNoteBusy =>
      'Ya se está guardando un cambio de nota. Reintenta cuando termine.';

  @override
  String get sessionNoteRefresh => 'Actualizar la nota guardada';

  @override
  String get sessionNoteSavedVersion =>
      'Nota guardada actual — revísala antes de reemplazarla';

  @override
  String get sessionNoteNone => 'Sin nota guardada';

  @override
  String get sessionNoteDiscard => '¿Descartar tus cambios de nota?';

  @override
  String get sessionNoteKeepEditing => 'Seguir editando';

  @override
  String get sessionNoteDiscardAction => 'Descartar cambios';

  @override
  String get sessionNoteDiscardDetail =>
      'Se perderán las ediciones que hiciste a esta nota. Esto no se puede deshacer.';

  @override
  String sessionNoteBytes(int used, int limit) {
    return '$used / $limit bytes';
  }

  @override
  String get usageTitle => 'Consumo y costo';

  @override
  String get usageDescription =>
      'Actividad registrada por este servidor de OpenCode en todas tus conversaciones.';

  @override
  String get usageRefresh => 'Actualizar el consumo';

  @override
  String get usageToday => 'Hoy';

  @override
  String get usageThirtyDays => '30 días';

  @override
  String get usageYear => 'Este año';

  @override
  String get usageAllTime => 'Todo el historial';

  @override
  String get usageScope => 'Alcance del proyecto';

  @override
  String get usageAllProjects => 'Todos los proyectos';

  @override
  String get usageCurrentProject => 'Proyecto actual';

  @override
  String get usageLoading => 'Cargando el consumo';

  @override
  String get usageUnsupported => 'Este servidor no admite el consumo agregado.';

  @override
  String get usageProjectUnavailable =>
      'No se pudo identificar un proyecto actual. Elige Todos los proyectos o abre un proyecto primero.';

  @override
  String get usageTimezoneUnavailable =>
      'No se pudo leer la zona horaria de este dispositivo. Reintenta para cargar el consumo con las fechas correctas.';

  @override
  String get usageRefreshInterrupted =>
      'El servidor cambió mientras se cargaba el consumo. Actualiza para reintentar.';

  @override
  String get usageInvalidResponse =>
      'El servidor devolvió datos de consumo incompletos. Actualiza para reintentar.';

  @override
  String get usageAuthorization =>
      'Revisa la contraseña y los permisos de este servidor y actualiza.';

  @override
  String get usagePreviousResult =>
      'Se muestra el resultado anterior para estos filtros.';

  @override
  String get usageLocationChanged =>
      'El servidor o el proyecto activos cambiaron. Reabre Consumo desde Ajustes.';

  @override
  String get usageTinyCost => 'Menos de \$0.000001';

  @override
  String get usageSessions => 'Conversaciones';

  @override
  String get usageSubagents => 'Conversaciones de sub-agentes';

  @override
  String get usagePrompts => 'Mensajes';

  @override
  String get usageSteps => 'Pasos del agente';

  @override
  String get usageActiveDays => 'Días activos';

  @override
  String get usageStreak => 'Racha más larga · días';

  @override
  String get usageEmpty =>
      'No hay actividad en este intervalo. Prueba uno más amplio o Todos los proyectos.';

  @override
  String get usageTokens => 'Tokens';

  @override
  String get usageTotalTokens => 'Total';

  @override
  String get usageInput => 'Entrada';

  @override
  String get usageOutput => 'Salida';

  @override
  String get usageReasoning => 'Razonamiento';

  @override
  String get usageCacheRead => 'Lectura de caché';

  @override
  String get usageCacheWrite => 'Escritura de caché';

  @override
  String get usageModels => 'Uso de modelos';

  @override
  String get usageNoModels =>
      'No se registró uso de modelos en este intervalo.';

  @override
  String get usageToolReliability => 'Fiabilidad de herramientas';

  @override
  String get usageToolsUnavailable =>
      'Esta respuesta no incluye la fiabilidad de herramientas.';

  @override
  String get usageNoTools =>
      'No se registraron llamadas a herramientas en este intervalo.';

  @override
  String get usageNoFinishedTools =>
      'Aún no hay llamadas a herramientas terminadas.';

  @override
  String get usageToolCalls => 'Llamadas';

  @override
  String get usageSucceeded => 'Exitosas';

  @override
  String get usageFailed => 'Fallidas';

  @override
  String get usageUnfinished => 'Sin terminar';

  @override
  String get usageCostDisclosure =>
      'Los costos son estimaciones que informa OpenCode, no una factura del proveedor. Las llamadas a herramientas sin terminar se excluyen de la tasa de éxito.';

  @override
  String usagePeriod(String from, String to) {
    return '$from – $to';
  }

  @override
  String usageTimezone(String timezone) {
    return 'Zona horaria: $timezone';
  }

  @override
  String usageModelSteps(String steps) {
    return '$steps pasos';
  }

  @override
  String usageModelTokens(String tokens) {
    return '$tokens tokens';
  }

  @override
  String usageSuccessRate(String rate) {
    return '$rate de las llamadas terminadas tuvieron éxito';
  }

  @override
  String usageUpdated(String time) {
    return 'Actualizado a las $time';
  }

  @override
  String get mcpRuntimeTitle => 'Hasta reiniciar el servidor';

  @override
  String get mcpDefaultLocation =>
      'Directorio predeterminado del servidor de OpenCode';

  @override
  String mcpWorkspaceLocation(String workspace) {
    return 'Entorno en la nube: $workspace';
  }

  @override
  String get mcpLocationChanged =>
      'El servidor o el proyecto cambiaron. Tu borrador sigue aquí; reabre la configuración en el proyecto correspondiente antes de agregarlo.';

  @override
  String get mcpAdding => 'Agregando servidor MCP';

  @override
  String get mcpAdd => 'Agregar servidor MCP';

  @override
  String get mcpRuntimeEmpty =>
      'Agrega herramientas para el proyecto actual hasta que OpenCode se reinicie.';

  @override
  String get mcpRuntimeAdded => 'Servidor MCP agregado para este proyecto';

  @override
  String get mcpHeaderName => 'Nombre del encabezado';

  @override
  String get mcpHeaderValue => 'Valor del encabezado';

  @override
  String get mcpAddHeader => 'Agregar otro encabezado';

  @override
  String get mcpRemoveHeader => 'Quitar el encabezado';

  @override
  String get sessionUnread => 'Resultado sin leer';

  @override
  String get shareSessionViewsTitle => 'Sincronizar estado de lectura';

  @override
  String get shareSessionViewsSaveError =>
      'No se pudo guardar esta preferencia. El uso compartido del estado leído queda desactivado en este dispositivo por ahora.';

  @override
  String get exportTitle => 'Exportar conversación';

  @override
  String get exportDescription =>
      'Elige un formato para guardar esta conversación en tu dispositivo.';

  @override
  String get exportJson => 'Conversación completa · JSON';

  @override
  String get exportJsonDescription =>
      'Descarga la conversación completa del servidor, incluidos los mensajes antiguos.';

  @override
  String get exportMarkdown => 'Transcripción legible · Markdown';

  @override
  String get exportMarkdownDescription =>
      'Guarda los mensajes cargados ahora en esta conversación. Carga primero los mensajes antiguos si los necesitas incluidos.';

  @override
  String get exportRedact => 'Ofuscar datos sensibles';

  @override
  String get exportUnredacted =>
      'El archivo sin ofuscar puede contener secretos, rutas locales y salida privada de herramientas.';

  @override
  String get exportCancel => 'Cancelar la descarga';

  @override
  String get exportDownloading => 'Descargando la conversación completa…';

  @override
  String get exportSaving => 'Guardando el archivo…';

  @override
  String get exportSaved => 'Conversación guardada';

  @override
  String get exportChanged =>
      'El servidor o el proyecto cambiaron. Reabre la exportación desde la conversación correspondiente.';

  @override
  String get exportUnsupported =>
      'Este servidor no admite la exportación JSON. Aun así puedes guardar la transcripción Markdown cargada.';

  @override
  String get exportAuthorization =>
      'El servidor negó el acceso. Revisa tus credenciales del servidor y reintenta.';

  @override
  String get exportMissing =>
      'Esta conversación ya no existe en el servidor. Aun así puedes guardar la transcripción Markdown cargada.';

  @override
  String get exportFailed =>
      'No se pudo exportar la conversación. Revisa tu conexión y tu almacenamiento, y reintenta.';

  @override
  String get importTitle => 'Importar conversación';

  @override
  String get importDescription =>
      'Restaura una exportación JSON en este servidor de OpenCode. Elige un archivo y revisa dónde se importará.';

  @override
  String get importChoose => 'Elegir archivo JSON';

  @override
  String get importChooseAnother => 'Elegir otro archivo';

  @override
  String get importAction => 'Importar conversación';

  @override
  String get importUntitled => 'Conversación sin título';

  @override
  String get importRedacted =>
      'Este archivo contiene marcadores de ofuscación. La importación no puede recuperar el texto original; usa una exportación sin ofuscar si lo necesitas.';

  @override
  String get importParent =>
      'Esta conversación fue iniciada por otra, que ya debe existir en este servidor. Importa primero esa.';

  @override
  String get importArchived =>
      'Esta conversación está archivada. La importación conservará su estado archivado.';

  @override
  String get importDestination => 'Importar en';

  @override
  String get importChooseDestination => 'Elige un proyecto en este servidor';

  @override
  String get importNoDestinations =>
      'No hay proyectos disponibles. Abre un proyecto en este servidor y reintenta.';

  @override
  String get importDestinationFailed =>
      'No se pudieron cargar los proyectos destino ni los entornos en la nube. Reintenta; tu archivo sigue seleccionado.';

  @override
  String get importPreserves =>
      'Tu archivo de origen queda igual. Nunca se reemplazan conversaciones existentes, e importar no inicia una ejecución del agente.';

  @override
  String get importReading => 'Preparando la importación…';

  @override
  String get importSending => 'Importando la conversación…';

  @override
  String get importSucceeded => 'Conversación importada';

  @override
  String get importOpen => 'Abrir la conversación';

  @override
  String get importOpenFailed =>
      'La conversación se importó, pero no se pudo abrir. Búscala en Todas las conversaciones del servidor destino.';

  @override
  String get importChanged =>
      'El servidor o el proyecto cambiaron. Tu archivo sigue aquí. Reabre la importación en el servidor correspondiente antes de continuar.';

  @override
  String get importUnsupported =>
      'Este servidor no admite la importación JSON.';

  @override
  String get importInvalidFile =>
      'Elige una exportación JSON de OpenCode válida, con información de conversación y registros de mensajes. Las transcripciones Markdown no se pueden importar.';

  @override
  String get importTooLarge =>
      'Este archivo supera el límite de importación móvil de 128 MiB. No se subió ni se recortó. Usa una computadora o una transferencia de servidor para este archivo.';

  @override
  String get importConflict =>
      'Ya existe en este servidor una conversación con este ID. No se reemplazó nada. Búscala en Todas las conversaciones, o importa este archivo en otro servidor.';

  @override
  String get importAuthorization =>
      'El servidor negó el acceso. Revisa tus credenciales del servidor. Tu archivo sigue seleccionado.';

  @override
  String get importParentMissing =>
      'Falta la conversación primaria en este servidor. Importa primero la primaria y luego reintenta este archivo.';

  @override
  String get importRejected =>
      'El servidor rechazó este formato de exportación. Tu archivo sigue seleccionado; revisa que viniera de un servidor de OpenCode compatible.';

  @override
  String get importUnconfirmed =>
      'No se pudo confirmar la importación. Revisa Todas las conversaciones antes de reintentar: es posible que el servidor ya la haya recibido. Tu archivo de fuente sigue igual.';

  @override
  String get sessionPinsLoadFailed =>
      'No se pudieron cargar algunas conversaciones fijadas. Actualiza para reintentar.';

  @override
  String get promptStashSaveFailed =>
      'No se pudo guardar este mensaje. Tu compositor no cambió. Revisa el almacenamiento del dispositivo y reintenta.';

  @override
  String get promptOriginalDraft => 'Restaurar el borrador original';

  @override
  String get promptStashTitle => 'Mensajes guardados';

  @override
  String get promptStashSearch => 'Buscar mensajes guardados';

  @override
  String get promptStashDeleteFailed =>
      'No se pudo borrar este mensaje guardado. Reintenta.';

  @override
  String get promptStashDelete => 'Borrar';

  @override
  String get promptStashFull =>
      'Tu alijo tiene 500 mensajes. Borra un mensaje guardado para hacer espacio; tu mensaje actual no cambia.';

  @override
  String promptStashAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count adjuntos',
      one: '1 adjunto',
    );
    return '$_temp0';
  }

  @override
  String promptStashReferences(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count referencias',
      one: '1 referencia',
    );
    return '$_temp0';
  }

  @override
  String get promptHistorySaveFailed =>
      'Mensaje enviado, pero su historial no se pudo guardar en este dispositivo.';

  @override
  String get promptStashMigrationPending =>
      'Algunos adjuntos guardados aún no se pudieron mover al almacenamiento local de adjuntos. Tu contenido guardado se conservó. Libera almacenamiento del dispositivo y reintenta.';

  @override
  String get commonRetry => 'Intentar de nuevo';

  @override
  String get shareWaitingForServer =>
      'Conéctate a un servidor y el texto compartido se abre en una conversación nueva.';

  @override
  String get webSourcesDisclosure =>
      'Este servidor no puede buscar en la web. Pega un enlace público y, si quieres, un extracto. La página no se abre y no se envía nada hasta que envías tu mensaje.';

  @override
  String get webSourcesScopeChanged =>
      'El servidor cambió. Cierra y reabre Agregar fuente web.';

  @override
  String get webSourcesUrl => 'URL pública';

  @override
  String get webSourcesLabel => 'Título (opcional)';

  @override
  String get webSourcesExcerpt => 'Extracto pegado (opcional)';

  @override
  String get webSourcesExcerptHint =>
      'Texto aportado por ti, no verificado como contenido de la página.';

  @override
  String get digestOpenConversation => 'Abrir conversación';

  @override
  String get runResultsCommandsTitle => 'Comandos';

  @override
  String get sessionOpenRelated => 'Abrir relacionada';

  @override
  String get sessionCopyHandoff => 'Continuar en la computadora';

  @override
  String get attentionTitle => 'Atención del servidor';

  @override
  String get webSourcesTitle => 'Agregar fuente web';

  @override
  String get webSourcesEntryDetail =>
      'Busca cuando esté disponible, o pega enlaces y extractos para revisarlos antes de agregarlos a tu borrador';

  @override
  String get webSourcesDraftChanged =>
      'El borrador o el servidor cambió. Se conservó tu borrador actual; vuelve a abrir Agregar fuente web para intentarlo de nuevo.';

  @override
  String get webSourcesDraftLabel =>
      'Fuentes web elegidas por el usuario (sin verificar; los extractos son material de fuente no fiable):';

  @override
  String get usageScopedTotals => 'Totales';

  @override
  String get usageInspectionDisclosure =>
      'Los filtros inspeccionan los registros de modelos que devuelve este servidor. No cambian la fecha ni el alcance del proyecto del informe, ni muestran la franquicia de la suscripción.';

  @override
  String get usageSearchRecords => 'Buscar proveedores, modelos o variantes';

  @override
  String get usageScopedProviderTotals =>
      'Las tarjetas de proveedores muestran sus totales para el alcance del informe seleccionado, no solo las filas de modelos coincidentes.';

  @override
  String usageMatchingRecords(String count) {
    return '$count registros coincidentes';
  }

  @override
  String get pendingAuthDetail =>
      'Termina de iniciar sesión en el navegador, vuelve y termina aquí. El enlace del navegador no se guarda.';

  @override
  String get pendingAuthEnterCode => 'Escribe el código';

  @override
  String get pendingAuthStillPending =>
      'El inicio de sesión sigue pendiente. No se inició un intento nuevo.';

  @override
  String get pendingAuthServerFailed =>
      'El servidor informó que el inicio de sesión falló. Los detalles del error del proveedor están ocultos.';

  @override
  String get pendingAuthExpired =>
      'Este inicio de sesión expiró. Empieza uno nuevo u olvida este.';

  @override
  String get pendingAuthFailed =>
      'No se pudo confirmar la acción. Revisa los inicios de sesión pendientes antes de reintentar. No se inició ningún inicio de sesión nuevo.';

  @override
  String get pendingAuthSaveUncertain =>
      'Este teléfono no pudo guardar el inicio de sesión para retomarlo después. Mantén la app abierta hasta que termine.';

  @override
  String get pendingAuthRetrySave => 'Reintentar guardar la recuperación';

  @override
  String get pendingAuthForget => 'Olvidar este inicio de sesión';

  @override
  String get pendingAuthUnsupported =>
      'Este servidor no puede recuperar inicios de sesión anteriores. Los inicios de sesión antiguos solo funcionan mientras su pantalla y conexión originales sigan disponibles.';

  @override
  String get pendingAuthOtherSource =>
      'Otros inicios de sesión pendientes pertenecen a otro servidor o proyecto. Vuelve a su fuente original para gestionarlos.';

  @override
  String get connectionHelpGuideTip =>
      'Mantén el servidor fuera del internet público. Accede por el HTTPS privado de Tailscale, o por un túnel cifrado que termine en el dispositivo que corre esta app. El localhost de tu computadora no es el localhost de tu teléfono.';

  @override
  String get voiceConversationTitle => 'Conversación por voz';

  @override
  String get voiceConversationDescription =>
      'Habla y luego toca Enviar: lo que dijiste llega al agente. Las respuestas solo se leen en voz alta si lo activas.';

  @override
  String get voiceConversationReplyReviewNeeded =>
      'La respuesta terminó, pero no se pudo emparejar con seguridad con tu mensaje. Léela si quieres.';

  @override
  String get voiceConversationReplyInterrupted =>
      'La respuesta necesitaba una decisión en pantalla, así que no se leyó en automático.';

  @override
  String get voiceConversationReplyNoProse =>
      'La respuesta no tiene texto para leer. El código y los detalles de herramientas no se dicen en voz alta.';

  @override
  String get voiceConversationReplyFailed =>
      'No se pudo leer la respuesta en voz alta.';

  @override
  String get voiceConversationPausedDetail =>
      'La conversación por voz está pausada. Reconecta, espera la respuesta o revisa las decisiones pendientes en pantalla.';

  @override
  String get voiceConversationDraftFirst =>
      'Envía, guarda o vacía tu borrador actual antes de empezar la conversación por voz.';

  @override
  String get voiceConversationCommandsOnly =>
      'Usa el cuadro de texto para los comandos con barra.';

  @override
  String get voiceInputUnavailable =>
      'La entrada de voz no está disponible. Revisa el modelo local y los ajustes del micrófono.';

  @override
  String get desktopDropFailedTitle =>
      'No se pudieron adjuntar los archivos soltados';

  @override
  String get desktopDropFailedRecovery =>
      'Revisa los adjuntos ya agregados antes de reintentar. También puedes usar el teclado para abrir Agregar y luego Adjuntar archivo.';

  @override
  String get commandAuthMethodHint =>
      'Ejecuta el método de inicio de sesión del proveedor en tu servidor seleccionado, no en este teléfono. Puede que necesites completar pasos interactivos en el servidor.';

  @override
  String get commandAuthStart => 'Iniciar el inicio de sesión del servidor';

  @override
  String get commandAuthPending =>
      'Iniciando sesión en el servidor… Completa los pasos que te pida allí. Cerrar esto no lo detiene.';

  @override
  String get commandAuthCancel => 'Cancelar el inicio de sesión';

  @override
  String get commandAuthFailed => 'El inicio de sesión no terminó.';

  @override
  String get commandAuthComplete => 'Conectado.';

  @override
  String get commandAuthExpired =>
      'El inicio de sesión agotó su tiempo antes de terminar.';

  @override
  String get commandAuthScopeChanged =>
      'Cambiaste a otro servidor o proyecto. Vuelve a él para ver este inicio de sesión.';

  @override
  String get commandAuthUncertainStart =>
      'Es posible que el servidor haya empezado el inicio de sesión. Revisa en el servidor antes de reintentar.';

  @override
  String get readAloudAction => 'Leer el texto de la respuesta';

  @override
  String get readAloudStop => 'Detener la lectura en voz alta';

  @override
  String get readAloudOtherVoice => 'Leer con otra voz';

  @override
  String get readAloudChooseVoice => 'Elegir una voz de lectura';

  @override
  String get readAloudConsentTitle => '¿Leer las respuestas en voz alta?';

  @override
  String get readAloudConsentDetail =>
      'El motor de voz del teléfono lee la respuesta en voz alta. El código y los detalles de herramientas se omiten. El teléfono recuerda tu respuesta.';

  @override
  String get readAloudContinue => 'Leer en voz alta';

  @override
  String get readAloudUnsupported =>
      'Leer en voz alta no está disponible en esta plataforma.';

  @override
  String get readAloudNoVoice =>
      'No hay una voz instalada sin conexión disponible. Configura una voz sin conexión en los ajustes de voz del sistema y reintenta.';

  @override
  String get readAloudUnavailable =>
      'El motor de voz no pudo leer esta respuesta. Reintenta o elige otra voz.';

  @override
  String get readAloudTooLong =>
      'Esta respuesta es demasiado larga para leerse en voz alta. Elige una respuesta más corta.';

  @override
  String get readAloudBusy =>
      'La reproducción de voz no está disponible mientras la captura de audio u otra interrupción de audio está activa.';

  @override
  String get readAloudNoProse =>
      'No hay texto de respuesta para leer. El código y los detalles de herramientas no se dicen en voz alta.';

  @override
  String get credentialMetadataOnly => 'Las claves permanecen en tu servidor.';

  @override
  String get credentialActiveUpdated =>
      'Cuenta activa actualizada desde el servidor.';

  @override
  String get credentialSwitchRequested =>
      'Cambio solicitado. Aún no lo confirma ningún evento del servidor.';

  @override
  String get credentialActive => 'Activa';

  @override
  String get credentialRename => 'Renombrar cuenta';

  @override
  String get credentialLabel => 'Etiqueta de la cuenta';

  @override
  String get credentialSave => 'Guardar etiqueta';

  @override
  String get credentialScopeChanged =>
      'El servidor o el proyecto cambió. Cierra y vuelve a abrir la gestión de cuentas antes de hacer cambios.';

  @override
  String get credentialProviderMissing =>
      'Este proveedor ya no está en la lista de integraciones del servidor.';

  @override
  String get credentialLoadFailed =>
      'No se pudieron actualizar las cuentas guardadas. Inténtalo de nuevo.';

  @override
  String get credentialMutationFailed =>
      'No se pudo confirmar el cambio de cuenta. Actualiza antes de reintentar; puede que el servidor ya lo haya aplicado.';

  @override
  String get credentialRefresh => 'Actualizar cuentas';

  @override
  String get credentialEmpty =>
      'No se informaron cuentas guardadas para este proveedor.';

  @override
  String get credentialEnvironment =>
      'Administrada por el entorno del servidor. No se puede quitar desde aquí.';

  @override
  String credentialUnnamed(int index) {
    return 'Cuenta guardada $index';
  }

  @override
  String get mcpRemoveFailed =>
      'No se pudo confirmar la eliminación de MCP. Actualiza la lista antes de reintentar; es posible que el servidor ya haya aplicado el cambio.';

  @override
  String get mcpLoadFailed =>
      'No se pudieron actualizar los datos de MCP. Reintenta.';

  @override
  String get mcpSavedStatus => 'Guardado en OpenCode';

  @override
  String get mcpRetryReconnect => 'Reintentar la reconexión';

  @override
  String get mcpReconnecting => 'Reconectando';

  @override
  String get mcpStillDisconnected => 'OpenCode sigue desconectado. Reintenta.';

  @override
  String get mcpScopeChanged =>
      'El servidor o el proyecto cambiaron. Actualiza para cargar sus servidores MCP antes de hacer cambios.';

  @override
  String get promptStashRestoreFailed =>
      'No se pudo terminar de restaurar el mensaje. Las copias guardadas siguen disponibles; revisa el compositor antes de reintentar.';

  @override
  String get promptStashContextOnly => 'Adjuntos y referencias';

  @override
  String get promptDefaultLocation =>
      'el directorio predeterminado del servidor';

  @override
  String get promptStashed => 'Mensaje guardado en tu alijo.';

  @override
  String get promptStashedDraftPending =>
      'Mensaje guardado en tu alijo. El borrador del compositor aún se debe guardar; reintenta desde la advertencia del borrador.';

  @override
  String get promptStashReadFailed =>
      'No se pudieron leer los mensajes guardados. Sus datos almacenados se conservaron.';

  @override
  String get promptStashDescription =>
      'Guarda texto, adjuntos y referencias para después';

  @override
  String get promptRestored => 'Mensaje guardado restaurado';

  @override
  String promptStashLocation(String directory) {
    return 'Este mensaje refiere archivos en $directory. Cambia a su proyecto original antes de restaurarlo.';
  }

  @override
  String get promptStashScopeChanged =>
      'El servidor o el proyecto cambiaron. Cierra y reabre Mensajes guardados.';

  @override
  String get transcriptFindHint => 'Buscar en la conversación';

  @override
  String get transcriptFindClose => 'Cerrar búsqueda';

  @override
  String get transcriptFindPrevious => 'Coincidencia anterior';

  @override
  String get transcriptFindNext => 'Siguiente coincidencia';

  @override
  String get transcriptFindNone => 'Sin coincidencias';

  @override
  String transcriptFindCount(int current, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$current de $total coincidencias',
      one: '1 coincidencia',
    );
    return '$_temp0';
  }

  @override
  String get transcriptFindPartial =>
      'Solo mensajes cargados. Carga mensajes anteriores para buscar más.';

  @override
  String get transcriptFindReasoning => 'Razonamiento';

  @override
  String get transcriptFindTool => 'Datos de herramienta';

  @override
  String get transcriptFindFile => 'Nombre de archivo';

  @override
  String get transcriptFindAll => 'Buscar en todo el historial';

  @override
  String get skillUse => 'Agregar a la conversación';

  @override
  String get skillActivationHelp =>
      'Agrega las instrucciones de la skill a esta conversación. Tu borrador sin enviar se queda en el compositor.';

  @override
  String get skillRunNow => 'Ejecutar el agente ahora';

  @override
  String get skillRunHelp =>
      'Desactívalo para agregar la skill sin iniciar otra respuesta.';

  @override
  String get skillLocationChanged =>
      'El servidor o el proyecto cambiaron. Reabre Skills desde la conversación.';

  @override
  String get skillUnsupported =>
      'La activación de skills no está disponible en este servidor. Aun así puedes previsualizar skills.';

  @override
  String get skillStaged =>
      'Resuelve el reverso preparado de la conversación antes de agregar una skill.';

  @override
  String get skillBusy => 'Ya se está agregando una skill a esta conversación.';

  @override
  String get skillUncertain =>
      'El servidor no confirmó el resultado. Es posible que la skill se haya agregado. Cierra esta vista y revisa la conversación antes de reintentar.';

  @override
  String get skillApplied => 'Habilidad añadida a esta conversación.';

  @override
  String get skillAppliedOriginal =>
      'Skill agregada a la conversación original. Cierra esta vista para volver.';

  @override
  String get activeContextTitle => 'Contexto activo';

  @override
  String get activeContextSubtitle =>
      'Inspeccionar mensajes tras la compactación';

  @override
  String get activeContextRefresh => 'Actualizar el contexto activo';

  @override
  String get activeContextSearch => 'Buscar mensajes activos';

  @override
  String get activeContextEmpty =>
      'El servidor no devolvió mensajes de contexto activo.';

  @override
  String get activeContextNoText =>
      'En esta entrada no hay contenido de texto admitido.';

  @override
  String get activeContextUnsupported =>
      'La inspección del contexto activo no está disponible en este servidor.';

  @override
  String get activeContextChanged =>
      'El servidor, el proyecto o la conversación cambiaron. Reabre este inspector desde la conversación.';

  @override
  String get activeContextInvalid =>
      'El servidor devolvió una instantánea de contexto inválida. Actualiza para reintentar.';

  @override
  String activeContextRefreshFailed(String error) {
    return 'Se muestra la instantánea anterior. No se pudo actualizar: $error';
  }

  @override
  String get activeContextContentHelp =>
      'Instantánea del contenido de mensajes disponible. Los cuerpos de adjuntos binarios, las URLs y los metadatos internos no se muestran. Esta no es la petición completa al proveedor.';

  @override
  String get activeContextUser => 'Mensaje del usuario';

  @override
  String get activeContextAssistant => 'Respuesta del asistente';

  @override
  String get activeContextSystem => 'Instrucciones del sistema';

  @override
  String get activeContextSynthetic => 'Mensaje sintético';

  @override
  String get activeContextSkill => 'Skill';

  @override
  String get activeContextShell => 'Shell';

  @override
  String get activeContextCompaction => 'Compactación';

  @override
  String get activeContextChange => 'Cambio de conversación';

  @override
  String get activeContextText => 'Texto';

  @override
  String get activeContextToolInput => 'Entrada de herramienta';

  @override
  String get activeContextToolOutput => 'Salida de herramienta';

  @override
  String get activeContextFile => 'Adjunto de archivo';

  @override
  String get activeContextNotice => 'Aviso del servidor';

  @override
  String get activeContextPruned => 'Contenido recortado por el servidor';

  @override
  String get activeContextTruncated => 'Salida recortada por el servidor';

  @override
  String get draftSaveFailed =>
      'Borrador sin guardar. Copia tu texto o reintenta.';

  @override
  String get draftStorageFull =>
      'El almacenamiento de borradores está lleno. Copia tu texto antes de salir.';

  @override
  String get draftProfileRemoved =>
      'Se eliminó el servidor original. Copia tu borrador para conservarlo.';

  @override
  String get draftRetrySave => 'Intentar guardar el borrador de nuevo';

  @override
  String get draftClearFailed =>
      'No se pudo borrar el borrador guardado. Inténtalo de nuevo antes de salir.';

  @override
  String activeContextTypeCount(String type, int count) {
    return '$type · $count';
  }

  @override
  String activeContextPartHeading(String kind, String name) {
    return '$kind · $name';
  }

  @override
  String get draftLeaveTitle => 'Tu borrador no está guardado';

  @override
  String get draftLeaveMessage =>
      'Copia tu texto para conservarlo o intenta guardar de nuevo. Si sales sin guardar, tus últimos cambios podrían perderse.';

  @override
  String get draftLeaveAction => 'Salir sin guardar';

  @override
  String get draftKeepEditing => 'Seguir editando';

  @override
  String get draftUnsaved => 'Sin guardar';

  @override
  String get draftAttachmentsLocal =>
      'Los adjuntos se guardan con este borrador en este dispositivo.';

  @override
  String get draftAttachmentsFailed =>
      'Los adjuntos necesitan recuperación o no se pudieron guardar. Inténtalo de nuevo antes de enviar.';

  @override
  String get draftAttachmentRecoveryTitle =>
      'Algunos adjuntos requieren atención';

  @override
  String draftAttachmentRecoveryDetail(String names) {
    return 'Estos adjuntos guardados faltan, no se pueden leer o pertenecen a otro proyecto: $names. Usa los adjuntos disponibles y quita estos del borrador, o conserva el borrador guardado e inténtalo más tarde.';
  }

  @override
  String get draftUseAvailableAttachments => 'Usar los adjuntos disponibles';

  @override
  String get draftKeepSavedAttachments => 'Conservar el borrador guardado';

  @override
  String get photoLibraryAction => 'Galería de fotos';

  @override
  String get photoLibraryDescription =>
      'Elige una foto o una captura de pantalla';

  @override
  String get photoCameraAction => 'Tomar foto';

  @override
  String get photoTooLarge => 'Elige una foto de menos de 10 MB.';

  @override
  String get photoStorageFailed =>
      'No se pudo guardar la foto en este dispositivo. Libera algo de espacio y reintenta.';

  @override
  String get photoPendingOther =>
      'Una foto sigue esperando en otra conversación. Agrégala o descártala allí e inténtalo de nuevo.';

  @override
  String get photoUnavailable =>
      'No se pudo abrir la foto. Intenta agregarla de nuevo desde la Biblioteca de fotos o con Tomar foto.';

  @override
  String get photoPermissionDenied =>
      'Se denegó el acceso a las fotos. Permite el acceso a la cámara o las fotos en los ajustes de Android e inténtalo de nuevo.';

  @override
  String get photoPendingTitle => 'Foto pendiente';

  @override
  String get photoDiscard => 'Descartar la foto pendiente';

  @override
  String get photoAddToDraft => 'Agregar la foto recuperada al borrador';

  @override
  String get photoOtherLocation =>
      'Vuelve al servidor y proyecto originales de la foto antes de agregarla.';

  @override
  String get photoDraftFull =>
      'Quita primero un adjunto. Un borrador admite hasta 5 archivos y 20 MB en total.';

  @override
  String get quotaCodex => 'Codex';

  @override
  String get quotaClaude => 'Claude';

  @override
  String get iosRemoteSummary =>
      'Un cliente remoto para el servidor de OpenCode que elijas. Alojar el servidor en el dispositivo y la supervisión en segundo plano no están disponibles en esta compilación para iOS.';

  @override
  String get iosKeychainGuide =>
      'Las contraseñas de los servidores usan el Keychain de este dispositivo. No se guardan en las preferencias comunes de la app.';

  @override
  String get platformSecureStorageGuide =>
      'Las contraseñas de los servidores usan el almacenamiento seguro de credenciales de esta plataforma. No se guardan en las preferencias comunes de la app.';

  @override
  String get usageProviders => 'Proveedores';

  @override
  String get usageProviderScope =>
      'Totales de los registros de modelos que devuelve este servidor para el alcance seleccionado. No es la facturación del proveedor ni franquicias de suscripción.';

  @override
  String usageProviderModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count modelos',
      one: '1 modelo',
    );
    return '$_temp0';
  }

  @override
  String get usageProviderCostUnavailable => 'Subtotal de costo no disponible';

  @override
  String usageProviderCostShare(String percent) {
    return '$percent del costo reportado';
  }

  @override
  String get setupOutputWaiting => 'Esperando la salida…';

  @override
  String get uncertainAuthDetail =>
      'Puede que el servidor haya iniciado este inicio de sesión sin confirmarlo. Revisa el servidor antes de empezar de nuevo.';

  @override
  String get uncertainAuthCloseHint =>
      'Para empezar de nuevo, cierra esto y borra el inicio de sesión no confirmado de la fila del proveedor.';

  @override
  String get pluginsUnsupported =>
      'Este servidor no admite inspeccionar plugins.';

  @override
  String get pluginsDisconnected =>
      'Conéctate a un servidor para inspeccionar sus plugins.';

  @override
  String get pluginsEmpty => 'No hay plugins reportados para este proyecto.';

  @override
  String get pluginsLoadFailed =>
      'No se pudieron cargar los plugins. Reintenta.';

  @override
  String get pluginsRetry => 'Reintentar';

  @override
  String get pluginsUnnamed => 'Plugin sin ID';

  @override
  String get pluginsStatusActive => 'Activo';

  @override
  String get pluginsStatusUnknown => 'Estado desconocido';

  @override
  String get pluginsSourceBuiltin => 'Incluido';

  @override
  String get pluginsSourcePackage => 'Paquete';

  @override
  String get pluginsSourceLocal => 'Archivo local (ruta oculta)';

  @override
  String get pluginsSourceSdk => 'SDK';

  @override
  String get pluginsSourceUnknown => 'Fuente desconocida';

  @override
  String get pluginsTerminalUi => 'Interfaz de terminal declarada';

  @override
  String get pluginsFailureDetail =>
      'Los detalles del fallo están ocultos porque pueden contener credenciales.';

  @override
  String get demoReviewChanges => 'Revisar cambios';

  @override
  String get demoSetUpServer => 'Configura tu propio servidor';

  @override
  String get handoffCopyCommand => 'Copiar comando';

  @override
  String get quotaMiniMax => 'MiniMax';

  @override
  String get quotaBudgetOff => 'Desactivado';

  @override
  String get quotaBudgetSaveFailed =>
      'No se pudo guardar este cambio del presupuesto. Tus últimos ajustes guardados siguen en vigor.';

  @override
  String get quotaGlm => 'GLM';

  @override
  String get usageBudgetTitle => 'Presupuestos';

  @override
  String get usageBudgetDescription =>
      'Los presupuestos usan todo el consumo reportado para el servidor, el proyecto, la zona horaria y el inicio de la ventana de fechas seleccionados. Los filtros de modelos no los cambian. Un inicio de ventana nuevo necesita un presupuesto nuevo. No cambian las franquicias de suscripción ni detienen peticiones.';

  @override
  String get usageBudgetUsd => 'Asignar presupuesto en USD';

  @override
  String get usageBudgetTokens => 'Asignar presupuesto de tokens';

  @override
  String get usageBudgetAmount => 'Monto del presupuesto';

  @override
  String get usageBudgetRemove => 'Quitar el presupuesto';

  @override
  String usageBudgetProgress(String used, String limit, String unit) {
    return '$used de $limit $unit';
  }

  @override
  String get usageBudgetTokenUnit => 'tokens';

  @override
  String get usageBudgetReached =>
      'Presupuesto personal alcanzado en esta lectura.';

  @override
  String get usageBudgetPrevious =>
      'Una lectura anterior alcanzó este presupuesto. Actualiza para revisar el consumo actual.';

  @override
  String get usageBudgetClearAll => 'Borrar ambos presupuestos';

  @override
  String get usageBudgetClearDescription =>
      '¿Quitar todos los presupuestos de consumo, presentes y pasados, de este servidor guardado? Los umbrales del proveedor se conservan.';

  @override
  String get usageBudgetClearTitle => '¿Borrar los presupuestos de consumo?';

  @override
  String get monitorScope =>
      'Los recuentos abarcan el último proyecto seleccionado de cada servidor, no todos los proyectos de ese servidor.';

  @override
  String get monitorDisclosure =>
      'La supervisión está desactivada hasta que la actives para un servidor. Las comprobaciones se hacen cada minuto aproximadamente mientras esta app está abierta. Las comprobaciones en segundo plano se hacen como máximo cada cinco minutos, solo mientras Mantener la conexión en segundo plano esté activado y el servicio de Android esté en ejecución. Android puede detener ese servicio; no se garantiza ningún tiempo restante.';

  @override
  String get monitorOptIn => 'Supervisar este servidor';

  @override
  String get monitorOptInDetail =>
      'Revisa permisos, preguntas y formularios pendientes en su último proyecto seleccionado.';

  @override
  String get monitorNotifications => 'Notificar cuando se necesite atención';

  @override
  String get monitorWifiDetail => 'Las revisiones se pausan sin Wi-Fi.';

  @override
  String get monitorQuiet => 'Horas de silencio';

  @override
  String get monitorQuietStart => 'Inicio de las horas de silencio';

  @override
  String get monitorQuietEnd => 'Fin de las horas de silencio';

  @override
  String get monitorSaveFailed =>
      'No se pudieron guardar los ajustes de supervisión. Inténtalo de nuevo.';

  @override
  String get monitorOpenFailed =>
      'Esta petición o su proyecto cambiaron. Actualiza la bandeja de entrada y reintenta.';

  @override
  String get monitorSession => 'Conversación';

  @override
  String get monitorUnknown => 'Desconocido';

  @override
  String get monitorLastChecked => 'Última revisión';

  @override
  String monitorRequestSummary(
    String profile,
    String kind,
    String lastChecked,
    String time,
  ) {
    return '$profile · $kind\n$lastChecked: $time';
  }

  @override
  String get monitorCheckIn => 'Revisar trabajos largos';

  @override
  String get monitorCheckInDetail => 'Un aviso tras el tiempo que elijas.';

  @override
  String get monitorCheckInDetailForeground =>
      'Una fila de aviso tras el tiempo que elijas.';

  @override
  String get monitorCheckInAfter => 'Revisar después de';

  @override
  String monitorMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes minutos',
      one: '1 minuto',
    );
    return '$_temp0';
  }

  @override
  String get monitorCheckInDue => 'Es momento de reportarse';

  @override
  String get pluginMappingUnavailable =>
      'Este plugin o comando ya no está disponible aquí. Actualiza y revisa tus vínculos.';

  @override
  String get mobileTasksUnfinished => 'Mostrar solo sin terminar';

  @override
  String get mobileTasksNoUnfinished =>
      'No hay tareas sin terminar en esta lista.';

  @override
  String get mobileTaskPending => 'Pendiente';

  @override
  String get mobileTaskInProgress => 'En progreso';

  @override
  String get mobileTaskCompleted => 'Completada';

  @override
  String get mobileTaskCancelled => 'Cancelada';

  @override
  String mobileTasksProgress(int done, int total) {
    return '$done de $total listas';
  }

  @override
  String get mobileTasksCopyAll => 'Copiar todas las tareas';

  @override
  String get mobileTaskPriorityHigh => 'Prioridad alta';

  @override
  String get mobileTaskPriorityMedium => 'Prioridad media';

  @override
  String get mobileTaskPriorityLow => 'Prioridad baja';

  @override
  String get usageWidgetTitle => 'Widget de consumo';

  @override
  String get usageWidgetIntro =>
      'Muestra lo que te queda en tus planes en el widget de consumo de la pantalla de inicio. Se actualiza mientras la app corre.';

  @override
  String get usageWidgetPhoneTitle => 'Este teléfono';

  @override
  String get usageWidgetSearchAliases =>
      'widget de consumo pantalla de inicio claude codex límite del plan restante paseo';

  @override
  String get quotaMonitorSourceChanged =>
      'La cuenta de este servidor cambió, así que las revisiones se detuvieron. Abre Consumo restante en ese servidor y léelo de nuevo.';

  @override
  String get webSearchDisclosure =>
      'La búsqueda envía tu consulta al proveedor de búsqueda seleccionado de este servidor. Revisa los resultados antes de agregarlos a tu borrador editable. Aquí no se envía nada al modelo.';

  @override
  String get webSearchManual => 'O pega una fuente';

  @override
  String get webSearchUnavailable =>
      'La búsqueda web no está disponible. Configura un proveedor de búsqueda en este servidor y actualiza los proveedores. Aun así puedes pegar una fuente abajo.';

  @override
  String get webSearchAuthentication =>
      'El servidor no autorizó la búsqueda web. Revisa las credenciales de este servidor.';

  @override
  String get webSearchInvalidResponse =>
      'La respuesta de la búsqueda no coincidió con este servidor ni con el formato admitido. Actualiza los proveedores o pega una fuente.';

  @override
  String get webSearchFailed =>
      'La búsqueda web no pudo terminar. Reintenta o pega una fuente.';

  @override
  String get webSearchRefresh => 'Actualizar proveedores';

  @override
  String get webSearchProvider => 'Proveedor de búsqueda';

  @override
  String get webSearchQuery => 'Consulta de búsqueda';

  @override
  String get webSearchSubmit => 'Buscar';

  @override
  String get webSearchEmpty => 'No hay resultados útiles para esta consulta.';

  @override
  String get webSearchOmitted =>
      'Se omitieron algunos resultados porque sus enlaces o extractos excedían los límites de revisión.';

  @override
  String get queueStorageUnreadable =>
      'No se pudieron leer los mensajes en cola guardados. No se pueden poner mensajes nuevos en cola hasta que se borren estos datos del dispositivo.';

  @override
  String get queueStorageDiscardUnreadable =>
      'Esto elimina de forma permanente de este dispositivo los mensajes en cola ilegibles y sus adjuntos. Se desconocen su contenido y su número. No se ve afectado nada en el servidor.';

  @override
  String get filesViewerScopeChanged =>
      'El servidor cambió. Cierra y vuelve a abrir este archivo.';

  @override
  String get queueStorageCountUnknown =>
      'No se pudieron leer los datos en cola guardados. Se desconoce el número de mensajes en cola.';

  @override
  String get codexConnectionVerified =>
      'Conexión verificada. Guarda y conecta para continuar.';

  @override
  String get codexApprovalRecoveryNotice =>
      'Tras reconectar, revisa las aprobaciones pendientes en tu computadora.';

  @override
  String connectionPasswordUnreadable(String server) {
    return 'No se puede leer la contraseña guardada de $server';
  }

  @override
  String connectionTokenUnreadable(String server) {
    return 'No se puede leer el token guardado de $server';
  }

  @override
  String get connectionEnterPassword => 'Introducir la contraseña';

  @override
  String get connectionEnterToken => 'Introducir el token';

  @override
  String get connectionPasswordUnreadableDetails =>
      'El almacenamiento seguro de este teléfono no pudo abrir la contraseña guardada para este servidor. Puede pasar tras restaurar el teléfono desde una copia de seguridad o al cambiar el bloqueo de pantalla. La contraseña no se modificó: vuelve a introducirla para conectar.';

  @override
  String get connectionTokenUnreadableDetails =>
      'El almacenamiento seguro de este teléfono no pudo abrir el token guardado para este servidor. Puede pasar tras restaurar el teléfono desde una copia de seguridad o al cambiar el bloqueo de pantalla. El token no se modificó: vuelve a introducirlo para conectar.';

  @override
  String get updateConnectionToken => 'Actualizar token';

  @override
  String get codexTextOnlyPrompt =>
      'Este servidor solo admite texto. Quita los adjuntos antes de enviar.';

  @override
  String get codexOfflineDraftSaved =>
      'Reconéctate antes de enviar. Tu borrador se guarda en este dispositivo.';

  @override
  String get codexReconnectBeforeSending => 'Reconéctate antes de enviar.';

  @override
  String get openCodeConnectionLabel => 'OpenCode';

  @override
  String get paseoAddressHint => 'ws://100.64.0.1:6767 o wss://paseo.example';

  @override
  String get paseoPasswordLabel => 'Contraseña del daemon (opcional)';

  @override
  String get paseoPasswordHelp =>
      'Defínela con \"paseo daemon set-password\". Se guarda en el almacenamiento seguro de este dispositivo.';

  @override
  String get paseoSetupNotice =>
      'Ejecuta \"paseo start --no-relay\" en la computadora que tenga Claude Code o Pi instalado. Esta app nunca usa el relay de Paseo: conéctate desde este dispositivo o por tu propia red privada.';

  @override
  String get connectionDisplayName => 'Nombre para mostrar (opcional)';

  @override
  String get connectionDisplayNameHint => 'Por defecto, el host del servidor';

  @override
  String get connectionServerAddress => 'Dirección del servidor';

  @override
  String get codexAddressHint => 'wss://codex.example o ws://127.0.0.1:4500';

  @override
  String get codexProjectFolder => 'Carpeta del proyecto en el servidor';

  @override
  String get codexTokenReentry => 'Vuelve a ingresar el token de conexión';

  @override
  String get codexTokenLabel => 'Token de conexión';

  @override
  String get codexTokenStorageHelp =>
      'Se guarda de forma segura en este dispositivo y se envía solo a este servidor Codex.';

  @override
  String get projectConfiguredFolder => 'Carpeta configurada';

  @override
  String get launchShortcutWaiting =>
      'Conectando con el servidor guardado. La conversación nueva se abre cuando esté listo.';

  @override
  String get launchShortcutNoServer =>
      'Elige un servidor y luego inicia una conversación nueva.';

  @override
  String get launchShortcutReentry =>
      'Ingresa las credenciales del servidor guardado y luego inicia una conversación nueva.';

  @override
  String get launchShortcutConnectionFailed =>
      'No se pudo conectar con el servidor guardado. Elige o corrige un servidor y luego inicia una conversación nueva.';

  @override
  String get launchUiPinnedUntitled => 'Conversación sin título';

  @override
  String get launchUiSessionWaiting =>
      'Conectando con el servidor guardado. La conversación se abre cuando esté lista.';

  @override
  String get launchUiSessionNoServer =>
      'Elige un servidor y luego abre la conversación desde su lista.';

  @override
  String get launchUiSessionReentry =>
      'Ingresa las credenciales del servidor guardado y luego abre la conversación desde su lista.';

  @override
  String get launchUiSessionConnectionFailed =>
      'No se pudo conectar con el servidor guardado. Elige o corrige un servidor y luego abre la conversación desde su lista.';

  @override
  String get launchUiSessionOtherServer =>
      'Ese acceso directo pertenece a otro servidor. Conéctate a ese servidor y luego abre la conversación desde su lista.';

  @override
  String get launchUiActivityNoServer =>
      'Elige un servidor para ver qué necesita tu atención.';

  @override
  String get launchUiUsageNoServer =>
      'Guarda un servidor para ver cuánto te queda en tus planes.';

  @override
  String get queuedResendTitle => '¿Enviar este borrador otra vez?';

  @override
  String get queuedResendMessage =>
      'Puede que ya haya llegado a OpenCode. Enviarlo otra vez podría duplicarlo.';

  @override
  String get queuedResendConfirm => 'Enviar otra vez';

  @override
  String get queuedKeepForReview => 'Conservar para revisar';

  @override
  String get queuedDiscardUnconfirmedMessage =>
      'Su envío anterior nunca se confirmó; puede que ya esté en la conversación.';

  @override
  String get setupRuntimeTwo => 'OpenCode 2';

  @override
  String queuedBannerReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count borradores con un envío sin confirmar por revisar.',
      one: '1 borrador con un envío sin confirmar por revisar.',
    );
    return '$_temp0';
  }

  @override
  String get isolatedTaskTitle => 'Empezar en una copia aparte';

  @override
  String get isolatedTaskIntro =>
      'Trabaja en su propia rama, así que no puede chocar con tus otras conversaciones.';

  @override
  String get isolatedTaskNameLabel => 'Nombre de la copia (opcional)';

  @override
  String get isolatedTaskNameHelper =>
      'Déjalo vacío y se elige un nombre por ti.';

  @override
  String get isolatedTaskStart => 'Iniciar';

  @override
  String get isolatedTaskCreating => 'Creando la copia…';

  @override
  String get isolatedTaskCreatingHint =>
      'Si dejas de esperar, es posible que la copia se cree igual. La encontrarás en Proyecto › Árboles de trabajo.';

  @override
  String isolatedTaskPreparing(String name) {
    return 'Preparando $name…';
  }

  @override
  String isolatedTaskReady(String name) {
    return '$name está lista. Abriendo la conversación…';
  }

  @override
  String isolatedTaskReadyIdle(String name) {
    return '$name está lista, pero la conversación no se abrió.';
  }

  @override
  String isolatedTaskUnconfirmed(String name) {
    return '$name está creada, pero su configuración no ha reportado de vuelta.';
  }

  @override
  String get isolatedTaskUnconfirmedHint =>
      'La configuración puede seguir en ejecución. Sigue esperando o empieza ahí mismo ahora.';

  @override
  String isolatedTaskFailed(String name) {
    return 'La configuración falló en $name';
  }

  @override
  String get isolatedTaskCreateFailed => 'No se pudo crear la copia';

  @override
  String get isolatedTaskCancelled => 'Se detuvo la espera.';

  @override
  String isolatedTaskOpening(String name) {
    return 'Abriendo la conversación en $name…';
  }

  @override
  String isolatedTaskOpened(String name) {
    return 'La conversación en $name está lista.';
  }

  @override
  String get isolatedTaskStopWaiting => 'Dejar de esperar';

  @override
  String get isolatedTaskKeepWaiting => 'Seguir esperando';

  @override
  String get isolatedTaskRetryOpen => 'Intentar de nuevo';

  @override
  String get isolatedTaskClose => 'Cerrar';

  @override
  String get returnBriefContinue => 'Continuar';

  @override
  String get capsuleError => 'Error';

  @override
  String get capsuleRemove => 'Quitar';

  @override
  String get markdownWrapCode => 'Ajustar líneas';

  @override
  String get markdownScrollCode => 'Desplazar líneas';

  @override
  String get markdownReaderTitle => 'Lector de código';

  @override
  String get tailscaleTitle => 'Conectar con Tailscale';

  @override
  String get tailscaleIntro =>
      'Accede a OpenCode en otra computadora a través de tu propia red de Tailscale.';

  @override
  String get tailscaleAppStep => '1. Abre tu red privada';

  @override
  String get tailscaleChecking => 'Buscando la app de Tailscale…';

  @override
  String get tailscaleInstalled => 'Tailscale está instalado.';

  @override
  String get tailscaleMissing =>
      'Tailscale no está instalado. Instala la app oficial, vuelve y comprueba de nuevo.';

  @override
  String get tailscaleUnknown =>
      'No se pudo comprobar la app. Inténtalo de nuevo o abre Tailscale desde tu teléfono.';

  @override
  String get tailscaleUnsupported =>
      'Este dispositivo no puede abrir la app de Android. Configura Tailscale en este dispositivo por tu cuenta y luego revisa tu dirección HTTPS abajo.';

  @override
  String get tailscaleVpnHandoff =>
      'En Tailscale, inicia sesión en la red que llega a tu servidor, acepta la solicitud de VPN de Android si aparece y activa la conexión. OpenCode no puede ver ni cambiar el estado de esa VPN.';

  @override
  String get tailscaleReturned =>
      'Te damos la bienvenida. Se volvió a comprobar la presencia de la app; usa Probar conexión en la siguiente pantalla para comprobar tu servidor.';

  @override
  String get tailscaleOpen => 'Abrir Tailscale';

  @override
  String get tailscaleCheckAgain => 'Comprobar Tailscale de nuevo';

  @override
  String get tailscaleAddressStep => '2. Revisa la dirección de tu servidor';

  @override
  String get tailscaleAddressLabel => 'Dirección del servidor privado';

  @override
  String get tailscaleAddressDetail =>
      'Pega la dirección HTTPS completa de Tailscale Serve, o la dirección HTTP y el puerto de tu servidor en Tailscale. HTTP pide tu permiso antes de conectar.';

  @override
  String get tailscaleAddressError =>
      'Introduce una dirección de servidor HTTP o HTTPS completa, con su puerto si hace falta y sin ruta después.';

  @override
  String get tailscaleReviewDetail =>
      'Continúa solo con una dirección que reconozcas. La siguiente pantalla revisa las credenciales de tu servidor antes de que pruebes o guardes de forma explícita. Esta app no puede confirmar que una dirección sea privada solo por su nombre.';

  @override
  String get tailscaleContinue => 'Continuar con el inicio de sesión';

  @override
  String get tailscaleHelp => 'Configuración y recuperación de Tailscale';

  @override
  String get tailscaleServeHelp =>
      'En la computadora del servidor, Tailscale Serve puede ofrecer HTTPS privado para un puerto local de OpenCode. Usa Serve, no Funnel público. Se siguen aplicando las reglas de acceso de tu tailnet. Al activar HTTPS, los nombres del dispositivo y de la tailnet del certificado se publican en un registro público de certificados, aunque el acceso sigue siendo privado. Revisa la guía oficial antes de cambiar tu servidor.';

  @override
  String get tailscaleServeDocs => 'Leer la guía oficial de Serve';

  @override
  String get tailscaleAndroidDocs => 'Leer la guía oficial de Android';

  @override
  String get tailscaleRecovery =>
      'Si el servidor no es accesible, revisa Tailscale en ambos dispositivos, la dirección y el puerto, y tus reglas de acceso de red. Si usas una dirección de Serve, revisa también Serve en el servidor. Corrige la contraseña si se rechaza la autenticación y vuelve a probar la conexión.';

  @override
  String get tailscaleEditorDetail =>
      'Tu conexión de red se gestiona en Tailscale. Probar conexión comprueba este servidor OpenCode, no la VPN. Introduce aquí el usuario y la contraseña del propio servidor, no tu inicio de sesión de Tailscale. La ayuda de configuración conserva estos campos.';

  @override
  String get a2aDraftSaveError =>
      'No se pudieron guardar los cambios del borrador. Mantén esta pantalla abierta y reintenta antes de salir.';

  @override
  String get a2aRetryDraftSave => 'Reintentar guardar el borrador';

  @override
  String get a2aSavingDraft => 'Guardando los cambios del borrador…';

  @override
  String get a2aSupportedConnection => 'A2A 1.0 · JSON-RPC · tareas de texto';

  @override
  String get a2aTitle => 'Agentes externos';

  @override
  String get a2aAdd => 'Agregar agente';

  @override
  String get a2aDeleteLocal => 'Quitar de este teléfono';

  @override
  String get a2aAddress => 'Dirección del agente';

  @override
  String get a2aUnsupported =>
      'No disponible: esta tarjeta no anuncia la combinación admitida de A2A 1.0 JSON-RPC, texto y autenticación en el mismo origen, o requiere una extensión no admitida. No se puede enviar ninguna tarea.';

  @override
  String get a2aBearer => 'Credencial de agente bearer';

  @override
  String get a2aSkills => 'Skills anunciadas';

  @override
  String get a2aTaskPrompt => 'Texto de la tarea';

  @override
  String get a2aDeliveryUnconfirmed => 'Entrega sin confirmar';

  @override
  String get a2aDraft => 'No enviado';

  @override
  String get a2aCancelDetail =>
      'Pide a este agente detener esta tarea. El trabajo puede ya haber terminado, y el agente decide si la detención es posible.';

  @override
  String get a2aYourReply => 'Tu respuesta';

  @override
  String get a2aAgentOutput => 'Salida del agente';

  @override
  String get a2aBlockedLink => 'Enlace no admitido';

  @override
  String get a2aReviewLink => 'Revisar el enlace externo';

  @override
  String get a2aOmittedContent =>
      'Parte de la salida se omite. Esta vista muestra texto acotado y enlaces; los artefactos binarios o estructurados no se descargan ni se ejecutan.';

  @override
  String get a2aSubmitted => 'Enviado';

  @override
  String get a2aWorking => 'Trabajando';

  @override
  String get a2aInputRequired => 'Se necesita tu respuesta';

  @override
  String get a2aAuthRequired => 'El agente requiere autenticación';

  @override
  String get a2aCompleted => 'Completado';

  @override
  String get a2aFailed => 'Fallido';

  @override
  String get a2aCanceled => 'Cancelado';

  @override
  String get a2aRejected => 'Rechazado';

  @override
  String get a2aUnknown => 'Estado de tarea no admitido';

  @override
  String get a2aAddressError =>
      'Usa un origen HTTPS o una URL pública de Agent Card sin credenciales, consulta ni fragmento. HTTP solo se admite en la dirección de loopback de este dispositivo.';

  @override
  String get a2aAuthenticationError =>
      'El agente rechazó o no pudo usar esta credencial. Vuelve al agente para actualizarla y reabre la tarea guardada.';

  @override
  String get a2aUnavailable =>
      'No se pudo contactar al agente o rechazó esta operación. Actualiza una tarea conocida para revisar su estado.';

  @override
  String get a2aInvalidResponse =>
      'El agente devolvió una respuesta no admitida, demasiado grande o inconsistente. La tarea guardada no se reemplazó.';

  @override
  String get a2aUncertain =>
      'Es posible que el agente haya recibido este mensaje. No se reenviará. Si un ID de tarea se confirmó, actualiza para revisar el progreso; de otra manera, consulta con el agente antes de empezar otra tarea.';

  @override
  String get a2aStorageError =>
      'No se pudo guardar o eliminar datos locales. Revisa el almacenamiento del dispositivo y reintenta la operación local. Un mensaje sin marcador de entrega guardado no se envía.';

  @override
  String get a2aScopeError =>
      'Este agente, credencial o tarea guardada cambió. Cierra esta vista y reabre el agente para continuar.';

  @override
  String get a2aCancelUnconfirmed =>
      'La cancelación no está confirmada. El agente sigue reportando una tarea activa; actualiza para revisar de nuevo.';

  @override
  String get a2aAuthRequiredDetail =>
      'Este agente pidió un flujo de autenticación adicional que este cliente no admite. No ocurrirá ningún inicio de sesión automático ni continuación de tarea.';

  @override
  String get a2aUnknownDetail =>
      'Este estado de tarea no está admitido. Puedes actualizar u olvidar el registro local; el envío y la cancelación siguen no disponibles.';

  @override
  String get fileTable => 'Tabla';

  @override
  String get fileSource => 'Origen';

  @override
  String fileLineOutsidePreview(int line) {
    return 'La línea $line está fuera de esta vista previa. Guarda el original para leer esa ubicación.';
  }

  @override
  String get fileTableMalformed =>
      'Este archivo tiene un entrecomillado incompleto o inconsistente. Lee su origen de datos en su lugar.';

  @override
  String get fileTableTooLarge =>
      'La vista previa de tablas admite archivos de hasta 256 KB. Lee el origen de datos o guarda el archivo original.';

  @override
  String get fileTableTooWide =>
      'Este archivo tiene más de 32 columnas. Lee el origen de datos o guarda el archivo original.';

  @override
  String get fileTableFieldTooLong =>
      'Una celda supera los 4096 caracteres. Lee el origen de datos o guarda el archivo original.';

  @override
  String get fileImage => 'Imagen';

  @override
  String get fileSvgUnsupported =>
      'Este SVG no se puede mostrar como imagen local estática. Lee su origen de datos o guarda el archivo original. Los recursos externos, la animación y las funciones SVG complejas no están admitidas.';

  @override
  String get filePdfEncrypted =>
      'Este PDF exige contraseña o usa una protección no admitida. Guarda el original para abrirlo en una app de PDF.';

  @override
  String get filePdfLimit =>
      'La vista previa de PDF admite archivos de hasta 10 MB y las primeras 200 páginas. Guarda el original para leer el documento completo.';

  @override
  String get filePdfUnavailable =>
      'La vista de PDF está disponible en Android 10 o posterior. Aun así puedes guardar el archivo original.';

  @override
  String get filePdfCancelled =>
      'Carga del PDF cancelada. Reintenta cuando estés listo.';

  @override
  String get filePdfFailed =>
      'No se pudo mostrar esta página del PDF. Reintenta o guarda el archivo original.';

  @override
  String get agentAccountTitle => 'Cuenta de Codex';

  @override
  String get agentAccountScopeLost =>
      'Este servidor cambió. Vuelve a Servidores y abre la cuenta del servidor conectado.';

  @override
  String get agentAccountRefresh => 'Actualizar la cuenta';

  @override
  String get agentAccountLoading => 'Revisando la cuenta del host';

  @override
  String get agentAccountUnavailable => 'Panel de cuenta no disponible';

  @override
  String get agentAccountReadFailed => 'No se pudo leer la cuenta';

  @override
  String get agentAccountDisconnected => 'Conexión interrumpida';

  @override
  String get agentAccountConnected => 'Sesión iniciada en el host';

  @override
  String get agentAccountSignedOut => 'Listo para iniciar sesión';

  @override
  String get agentAccountInProgress => 'Inicio de sesión en curso';

  @override
  String get agentAccountNeedsAttention =>
      'El inicio de sesión requiere tu atención';

  @override
  String get agentAccountNoAuth => 'El host no exige iniciar sesión';

  @override
  String get agentAccountApiKey => 'Clave API';

  @override
  String get agentAccountHostAuth => 'Autenticación del host';

  @override
  String get agentAccountHostNote =>
      'El inicio de sesión se conserva en la computadora del servidor, no en esta app. Iniciar o cerrar sesión aquí lo cambia en cada servidor guardado de esa computadora.';

  @override
  String get agentAccountUnsupportedDetail =>
      'Este panel está verificado con Codex 0.153.4. Es posible que el runtime conectado no admita estos métodos de cuenta.';

  @override
  String get agentAccountReconnectDetail =>
      'Se borraron los datos de cuenta y el código de inicio de sesión. Reconecta para actualizar. El inicio de sesión no se reiniciará solo.';

  @override
  String get agentAccountSignIn => 'Iniciar sesión con ChatGPT';

  @override
  String get agentAccountSignInNote =>
      'Inicia sesión con tu cuenta de ChatGPT. Terminas en el navegador; esta app nunca ve tu contraseña.';

  @override
  String get agentAccountLimits => 'Límites de tasa';

  @override
  String get agentAccountLimitsUnavailable =>
      'Los límites de tasa no están disponibles para esta cuenta o host.';

  @override
  String get agentAccountUsage => 'Uso de tokens';

  @override
  String get agentAccountUsageUnavailable =>
      'El uso de tokens no está disponible para esta cuenta o host.';

  @override
  String get agentAccountLifetimeTokens => 'Tokens de por vida';

  @override
  String get agentAccountPeakTokens => 'Pico diario de tokens';

  @override
  String get agentAccountUsageNote =>
      'Los valores los reporta el host. Un valor faltante es desconocido, no cero. Los conteos de tokens no son una factura ni los mensajes que te quedan.';

  @override
  String agentAccountUpdated(String time) {
    return 'Última revisión $time';
  }

  @override
  String get agentAccountStarting => 'Pidiendo un código de inicio de sesión';

  @override
  String get agentAccountWaiting =>
      'Termina el inicio de sesión en tu navegador';

  @override
  String get agentAccountCancelling => 'Cancelando el inicio de sesión';

  @override
  String get agentAccountCancelled => 'Inicio de sesión cancelado';

  @override
  String get agentAccountLoginFailed =>
      'El inicio de sesión no se completó. Revisa el host y reintenta.';

  @override
  String get agentAccountLoginUncertain =>
      'El host no pudo confirmar el inicio de sesión ni su cancelación. Puede seguir esperando. Revisa el runtime oficial del host antes de empezar de nuevo.';

  @override
  String get agentAccountLoginCompleted =>
      'Inicio de sesión completado. Revisando la cuenta.';

  @override
  String get agentAccountCodeHint =>
      'Escribe este código de un solo uso en la página oficial de inicio de sesión. Mantenlo privado.';

  @override
  String get agentAccountOpenSignIn => 'Abrir el inicio de sesión oficial';

  @override
  String get agentAccountCancel => 'Cancelar el inicio de sesión';

  @override
  String get agentAccountAllowance => 'Franquicia reportada';

  @override
  String agentAccountPercentUsed(int percent) {
    return '$percent% usado';
  }

  @override
  String get agentAccountWindowUnknown =>
      'Duración de la ventana no disponible';

  @override
  String agentAccountWindowMinutes(int minutes) {
    return 'Ventana de $minutes minutos';
  }

  @override
  String agentAccountWindowHours(int hours) {
    return 'Ventana de $hours horas';
  }

  @override
  String agentAccountWindowDays(int days) {
    return 'Ventana de $days días';
  }

  @override
  String get agentAccountResetUnknown => 'Hora de reajuste no disponible';

  @override
  String get projectFolderCreate => 'Crear una carpeta nueva';

  @override
  String get projectFolderOpen => 'Abrir una carpeta de proyecto';

  @override
  String get projectFolderNameLabel => 'Nombre de la carpeta';

  @override
  String get projectFolderNameHint => 'my-app';

  @override
  String get projectFolderCreateAction => 'Crear';

  @override
  String get projectFolderCancel => 'Cancelar';

  @override
  String get projectFolderOpenMessage =>
      'Escribe la ruta completa de una carpeta en el servidor. La carpeta home no se puede usar; elige un proyecto dentro de ella.';

  @override
  String get projectFolderPathLabel => 'Ruta de la carpeta';

  @override
  String projectFolderPathHint(String directory) {
    return '$directory/mi-app';
  }

  @override
  String get projectFolderOpenAction => 'Abrir';

  @override
  String projectFolderCreateSubtitle(String directory) {
    return 'En $directory de este dispositivo';
  }

  @override
  String get projectFolderOpenSubtitle =>
      'Escribe la ruta completa de una carpeta en el servidor';

  @override
  String get globalSessionsTitle => 'Todas las conversaciones';

  @override
  String get globalSessionsSearchLabel => 'Buscar títulos de conversaciones';

  @override
  String get globalSessionsArchivedShort => 'Archivadas';

  @override
  String get globalSessionsUnknownLocation => 'Proyecto desconocido';

  @override
  String get globalSessionsEmptyTitle => 'Aún no hay conversaciones';

  @override
  String get globalSessionsEmptyMessage =>
      'Aquí aparecerán las conversaciones de todos los proyectos de este servidor.';

  @override
  String get globalSessionsNoMatchTitle => 'No hay conversaciones coincidentes';

  @override
  String get globalSessionsNoMatchMessage =>
      'Prueba una búsqueda de título más corta o incluye las conversaciones archivadas.';

  @override
  String get globalSessionsRefresh => 'Actualizar';

  @override
  String get globalSessionsLoadMoreFailed =>
      'No se pudieron cargar más conversaciones';

  @override
  String get globalSessionsOpen => 'Abrir';

  @override
  String get globalSessionsContinueHere => 'Continuar aquí';

  @override
  String get globalSessionsActions => 'Acciones de la conversación';

  @override
  String get globalSessionsWorking => 'Trabajando';

  @override
  String get globalSessionsUntitled => 'Conversación sin título';

  @override
  String get workspaceNewSession => 'Nueva conversación';

  @override
  String get workspaceDismissNotice => 'Cerrar';

  @override
  String get reviewCopyFile => 'Copiar el archivo actualizado';

  @override
  String get reviewCopyPatch => 'Copiar el parche';

  @override
  String get onboardingPrivateNetwork =>
      'Accede a un servidor por tu red privada';

  @override
  String get onboardingSetupGuide => 'Guía de configuración';

  @override
  String get onboardingSaveConnect => 'Guardar y conectar';

  @override
  String get onboardingSaveChanges => 'Guardar cambios';

  @override
  String get onboardingTermuxSetup => 'En este teléfono';

  @override
  String get activitySavedServers => 'Servidores guardados';

  @override
  String get demoTaskTitle => 'Prueba un cambio pequeño';

  @override
  String get demoTaskInstruction =>
      'Envía el mensaje de ejemplo de abajo y luego revisa la edición propuesta.';

  @override
  String get reviewTitle => 'Revisar';

  @override
  String get modelChoiceReloadProviders => 'Recargar proveedores';

  @override
  String get modelChoiceDone => 'Listo';

  @override
  String get modelChoicePartialSaveError =>
      'Modelo guardado. No se confirmó el agente elegido. Inténtalo de nuevo.';

  @override
  String get modelChoiceModelSaveError =>
      'No se pudo confirmar el modelo elegido. Revisa tu selección e inténtalo de nuevo.';

  @override
  String get workIdle => 'Inactivo';

  @override
  String get workStartedInBackground => 'Iniciado en segundo plano';

  @override
  String get workBackgroundPending => 'Pidiendo trabajo en segundo plano…';

  @override
  String get workBackgroundRequested =>
      'Se solicitó trabajo en segundo plano. El estado se actualizará cuando el servidor lo informe.';

  @override
  String get oc2DiscoveryEditorTitle => 'OpenCode 2';

  @override
  String get setupSwitchFailed =>
      'No se pudo terminar la actualización a OpenCode 2. Tus conversaciones guardadas se conservan. Inténtalo de nuevo.';

  @override
  String e7ConnectionFailure1(int attempts) {
    return 'Se intentó $attempts veces. Reintentar no iniciará un servidor que no está en ejecución.';
  }

  @override
  String get e7ConnectionFailure2 => 'Se requiere un token de conexión';

  @override
  String get e7ConnectionFailure3 =>
      'Este servidor de Codex necesita un token de conexión para que la app pueda conectarse.';

  @override
  String get e7ConnectionFailure4 =>
      'Abre los ajustes del servidor e introduce el token de conexión de Codex.';

  @override
  String get e7ConnectionFailure5 => 'Token de conexión rechazado';

  @override
  String get e7ConnectionFailure6 =>
      'El servidor de Codex respondió, pero no aceptó el token de conexión guardado.';

  @override
  String get e7ConnectionFailure7 =>
      'Abre los ajustes del servidor e introduce un token de conexión de Codex vigente.';

  @override
  String get e7ConnectionFailure8 => 'Listener de Codex no disponible';

  @override
  String get e7ConnectionFailure9 => 'Endpoint de Codex inaccesible';

  @override
  String get e7ConnectionFailure12 =>
      'Inicia el listener de Codex en este dispositivo.';

  @override
  String get e7ConnectionFailure13 =>
      'Si está detrás de un túnel, mantén el túnel activo y verifica su endpoint local.';

  @override
  String get e7ConnectionFailure14 =>
      'Usa el endpoint wss:// de Codex o un túnel seguro activo.';

  @override
  String get e7ConnectionFailure15 =>
      'Comprueba que el listener remoto de Codex sea accesible desde este dispositivo.';

  @override
  String get e7ConnectionFailure16 => 'Contraseña rechazada';

  @override
  String get e7ConnectionFailure17 =>
      'El servidor respondió, pero no aceptó la contraseña guardada. Esto pasa cuando el servidor se reinició con una contraseña nueva.';

  @override
  String get e7ConnectionFailure18 =>
      'Ejecuta opencode2 pair en la computadora y pega el código nuevo.';

  @override
  String get e7ConnectionFailure19 =>
      'Si definiste OPENCODE_SERVER_PASSWORD a mano, cópiala de nuevo.';

  @override
  String get e7ConnectionFailure20 => 'Certificado no confiable';

  @override
  String get e7ConnectionFailure21 =>
      'El servidor está ahí, pero este dispositivo no confía en su certificado HTTPS, así que la app se negó a enviar la contraseña.';

  @override
  String get e7ConnectionFailure22 =>
      'Usa un certificado de una autoridad de confianza o una dirección de Tailscale Serve.';

  @override
  String get e7ConnectionFailure23 =>
      'Con un certificado autofirmado, edita este servidor y revisa su huella antes de confiar.';

  @override
  String get e7ConnectionFailure24 => 'Nada respondió en este teléfono';

  @override
  String get e7ConnectionFailure27 =>
      '¿Usas adb reverse o un reenvío SSH? Comprueba que el túnel siga conectado e inténtalo de nuevo.';

  @override
  String get e7ConnectionFailure28 =>
      '¿Te conectas a otra computadora? Cambia el servidor a su dirección HTTPS o vuelve a emparejar.';

  @override
  String get e7ConnectionFailure29 => 'El servidor no respondió a tiempo';

  @override
  String get e7ConnectionFailure31 =>
      '¿Estás en la misma red o VPN (por ejemplo, Tailscale) que la computadora?';

  @override
  String e7ConnectionFailure32(int port) {
    return '¿Un firewall o un portal cautivo bloquea el puerto $port?';
  }

  @override
  String get e7ConnectionFailure33 => 'Servidor inaccesible';

  @override
  String get e7ConnectionFailure35 =>
      '¿Sigue en ejecución opencode serve en la computadora?';

  @override
  String get e7ConnectionFailure36 =>
      '¿Estás en la misma red o VPN que la computadora?';

  @override
  String get e7ConnectionFailure37 =>
      '¿Cambió la dirección? Vuelve a emparejar para obtener la nueva.';

  @override
  String get e7ConnectionFailure38 => 'El servidor respondió con un error';

  @override
  String get e7ConnectionFailure39 =>
      'El servidor está en ejecución, pero se reportó como no saludable. Su propio registro dirá por qué.';

  @override
  String get e7ConnectionFailure40 =>
      'Reinicia opencode serve y observa su salida.';

  @override
  String get e7ConnectionFailure41 =>
      'Comprueba que esta app admita la versión del servidor.';

  @override
  String get e7ConnectionFailure42 => 'No se pudo conectar';

  @override
  String get e7ConnectionFailure44 =>
      '¿Está en ejecución el servidor del agente y es esta la dirección correcta?';

  @override
  String get e7ConnectionFailure45 =>
      '¿Está en ejecución opencode serve y es esta la dirección correcta?';

  @override
  String get e7PermissionAction1 => 'Ejecutar un comando de shell';

  @override
  String get e7PermissionAction2 => 'Editar un archivo';

  @override
  String get e7PermissionAction3 => 'Leer un archivo';

  @override
  String get e7PermissionAction4 => 'Acceder a un directorio externo';

  @override
  String get e7PermissionAction5 => 'Continuar tras fallos repetidos';

  @override
  String get e7PermissionAction6 => 'Se necesita permiso';

  @override
  String e7PermissionAction7(String permission) {
    return 'Usar $permission';
  }

  @override
  String get e7GlossaryMcpExplanation =>
      'Model Context Protocol. Pequeños servidores de complemento que le dan al agente herramientas extra, como un navegador, una base de datos o una herramienta de diseño. Los conectas una vez y todas las conversaciones pueden usarlos.';

  @override
  String get e7GlossaryWorktreeExplanation =>
      'Un checkout aparte del mismo repositorio. Úsalo cuando quieras que el agente pruebe algo en su propia rama sin tocar el código en el que trabajas.';

  @override
  String get e7GlossaryGotIt => 'Entendido';

  @override
  String get e7BannerUpdatePassword => 'Actualizar contraseña';

  @override
  String get e7BannerLost => 'Conexión perdida';

  @override
  String get e7BannerDetails => 'Detalles';

  @override
  String e7BannerReconnectingServer(String server) {
    return 'Reconectando con $server…';
  }

  @override
  String e7BannerReconnectingServerSemantic(String server) {
    return 'Reconectando con $server';
  }

  @override
  String get e7BannerCheckingExplanation =>
      'Lo que ves sigue disponible mientras se comprueba OpenCode. Las actualizaciones en vivo se reanudan solas.';

  @override
  String get e7BannerStaleExplanation =>
      'Lo que ves puede estar desactualizado hasta que OpenCode vuelva a estar accesible.';

  @override
  String e7BannerCheckingExplanationAgent(String agent) {
    return 'Lo que ves sigue disponible mientras $agent se revisa. Las actualizaciones en vivo se reanudan solas.';
  }

  @override
  String e7BannerStaleExplanationAgent(String agent) {
    return 'Lo que ves puede estar desactualizado hasta que $agent vuelva a ser alcanzable.';
  }

  @override
  String get e7SharedThreeStepsToYourFirstSession =>
      'Tres pasos para tu primera conversación';

  @override
  String get e7SharedOpenCodeRunsOnYourComputerThisApp =>
      'OpenCode corre en tu computadora. Esta app es el control remoto. El emparejamiento conecta los dos con un solo comando — sin direcciones ni contraseñas que escribir.';

  @override
  String get e7SharedOnYourComputerRunOneCommand =>
      'En tu computadora, ejecuta un comando';

  @override
  String get e7SharedInATerminalOnTheComputerWhere =>
      'En una terminal de la computadora donde está instalado OpenCode:';

  @override
  String get e7SharedItStartsTheServerAndPrintsA =>
      'Inicia el servidor y muestra un código de emparejamiento — y un código QR que puedes escanear.';

  @override
  String get e7SharedScanTheQROrPasteTheCode =>
      'Escanear el QR o pegar el código en esta app';

  @override
  String get e7SharedPasteTheCodeInThisApp => 'Pega el código en esta app';

  @override
  String get e7SharedStartTalking => 'Empieza a conversar';

  @override
  String get e7SharedPickAProjectAndSendYourFirst =>
      'Elige un proyecto y envía tu primer mensaje. El trabajo ocurre en tu computadora; esta app lo muestra y te deja dirigir.';

  @override
  String get e7SharedAdvanced => 'Avanzado';

  @override
  String get e7SharedHTTPSSSHTunnelsOlderServers =>
      'HTTPS, túneles SSH, servidores antiguos';

  @override
  String get e7SharedReachAServerOverHTTPSOrA =>
      'Alcanza un servidor por HTTPS o por un túnel';

  @override
  String get e7SharedPairingWorksWhenTheAddressTheServer =>
      'El emparejamiento funciona cuando este dispositivo puede alcanzar la dirección del servidor. Una dirección HTTP remota pide tu permiso antes de enviar la contraseña. Prefiere HTTPS o una conexión privada de Tailscale.';

  @override
  String get e7SharedOlderServersWithoutPairing =>
      'Servidores antiguos sin emparejamiento';

  @override
  String get e7SharedServersStartedWithOpencodeServeDoNot =>
      'Para una conexión local, inicia OpenCode 2 en esta computadora. Usa la contraseña que muestra:';

  @override
  String get e7SharedThenAddTheServerManuallyWithUsername =>
      'Luego agrega el servidor manualmente con el usuario opencode y esa contraseña.';

  @override
  String get e7SharedSecurityNotes => 'Notas de seguridad';

  @override
  String get e7SharedAlwaysSetOPENCODESERVERPASSWORDWhenBinding =>
      'Define siempre OPENCODE_SERVER_PASSWORD cuando expongas más allá del localhost.';

  @override
  String get e7SharedPasswordsAreStoredInTheAndroidKeystore =>
      'Las contraseñas se guardan en el Android Keystore, solo en este dispositivo.';

  @override
  String get e7SharedTheServerCanExecuteCommandsOnIts =>
      'El servidor puede ejecutar comandos en su host — trata el acceso como un acceso SSH.';

  @override
  String get e7SharedOpenCodeIsReconnectingTryAgain =>
      'OpenCode está reconectando. Reintenta.';

  @override
  String get e7SharedSessionContext => 'Contexto de la conversación';

  @override
  String get e7SharedRefreshContext => 'Actualizar el contexto';

  @override
  String get e7SharedNoContextUsageYet => 'Aún no hay uso de contexto';

  @override
  String get e7SharedSendAPromptAndWaitForAn =>
      'Envía un mensaje y espera una respuesta del asistente. OpenCode reportará entonces el uso de tokens de esta conversación.';

  @override
  String get e7SharedEstimatedInputMakeup =>
      'Composición estimada de la entrada';

  @override
  String get e7SharedSessionTotals => 'Totales de la conversación';

  @override
  String get e7SharedUsageComesFromTheLatestCompletedAssistant =>
      'El uso viene del último mensaje del asistente completado. La composición es una estimación por el texto visible de mensaje, respuesta y herramientas; “Otro” incluye instrucciones de sistema, definiciones de herramientas y la sobre-ingesta del proveedor.';

  @override
  String get e7SharedModelUnavailable => 'Modelo no disponible';

  @override
  String get e7SharedContextLimitUnavailable =>
      'Límite de contexto no disponible';

  @override
  String get e7SharedLatestAssistantRequestIncludingCacheActivity =>
      'Última petición del asistente, incluida la actividad de caché';

  @override
  String get e7SharedContextLimit => 'Límite de contexto';

  @override
  String get e7SharedUnavailable => 'No disponible';

  @override
  String get e7SharedMessages => 'Mensajes';

  @override
  String get e7SharedAccumulatedCostReportedByServer =>
      'Costo acumulado · reportado por el servidor';

  @override
  String get e7SharedAccumulatedCost => 'Costo acumulado';

  @override
  String get e7SharedSessionTokensReportedByServer =>
      'Tokens de la conversación · reportados por el servidor';

  @override
  String get e7SharedUserPrompts => 'Mensajes del usuario';

  @override
  String get e7SharedAssistantText => 'Texto del asistente';

  @override
  String get e7SharedToolCallsAndResults =>
      'Llamadas a herramientas y resultados';

  @override
  String get e7SharedOtherContext => 'Otro contexto';

  @override
  String get e7SharedSessionLocationChangedCloseAndReopenThis =>
      'El proyecto de la conversación cambió. Cierra y reabre esta vista.';

  @override
  String get e7SharedOpenCodeIsReconnecting => 'OpenCode está reconectando.';

  @override
  String get e7SharedTheSessionProjectIsNotAvailableOn =>
      'El proyecto de la conversación no está disponible en este servidor.';

  @override
  String get e7SharedLocalProject => 'Proyecto local';

  @override
  String get e7SharedTheAppCouldNotInspectWorkingChanges =>
      'La app no pudo inspeccionar los cambios en trabajo. Por seguridad, esto continúa sin transferir cambios.';

  @override
  String get e7SharedMoveSession => 'Mover la conversación';

  @override
  String get e7SharedChooseAnotherDirectoryInThisProject =>
      'Elige otro directorio de este proyecto.';

  @override
  String get e7SharedChooseAConnectedWorkspaceOrReturnTo =>
      'Elige un entorno en la nube conectado o vuelve al proyecto local.';

  @override
  String get e7SharedFilterDestinations => 'Filtrar destinos';

  @override
  String get e7SharedCurrent => 'Actual';

  @override
  String get e7SharedSwitchOrganization => '¿Cambiar de organización?';

  @override
  String get e7SharedSwitchOrganization462 => 'Cambiar de organización';

  @override
  String get e7SharedNoSwitchableOpenCodeConsoleOrganizationsWereReturned =>
      'No se devolvieron organizaciones de OpenCode Console que se puedan cambiar.';

  @override
  String get e7SharedSessionLocationChangedReturnAndReopenRelated =>
      'El proyecto de la conversación cambió. Vuelve y reabre las conversaciones relacionadas.';

  @override
  String get e7SharedSessionIsNoLongerRelatedToThis =>
      'Esa conversación ya no está relacionada con esta.';

  @override
  String get e7SharedSessionLocationChangedReturnAndTryAgain =>
      'El proyecto de la conversación cambió. Vuelve o actualiza y reintenta.';

  @override
  String get e7SharedSessionUnavailableOrLocationChangedReturnOr =>
      'Conversación no disponible o su proyecto cambió. Vuelve o actualiza y reintenta.';

  @override
  String get e7SharedCouldNotUpdateThePinReturnAnd =>
      'No se pudo actualizar el elemento fijado. Vuelve y reintenta.';

  @override
  String get e7SharedRefreshSubagentSessions =>
      'Actualizar las conversaciones de sub-agentes';

  @override
  String get e7SharedParentSession => 'Conversación primaria';

  @override
  String get e7SharedNoSubagentSessionsYet =>
      'Aún no hay conversaciones de sub-agentes';

  @override
  String get e7SharedDelegatedWorkWillAppearHereWithoutMixing =>
      'El trabajo delegado aparecerá aquí, sin mezclar las conversaciones de sub-agentes con tu lista principal.';

  @override
  String get e7SharedOpenInsecureHTTPLink => '¿Abrir enlace HTTP no seguro?';

  @override
  String get e7SharedOpenExternalLink => '¿Abrir enlace externo?';

  @override
  String get e7SharedHTTPIsNotEncryptedOtherDevicesOn =>
      'HTTP no está cifrado. Otros dispositivos de la red pueden leer o modificar lo que envías y recibes.';

  @override
  String get e7SharedOpenHTTPLink => 'Abrir enlace HTTP';

  @override
  String get e7SharedOpenLink => 'Abrir enlace';

  @override
  String get e7SharedNoAppCouldOpenThisLink =>
      'Ninguna app pudo abrir este enlace.';

  @override
  String get e7SharedRequired => 'Obligatorio';

  @override
  String get e7SharedDoesNotMatchTheExpectedFormat =>
      'No coincide con el formato esperado';

  @override
  String get e7SharedEnterAWholeNumber => 'Escribe un número entero';

  @override
  String get e7SharedEnterANumber => 'Escribe un número';

  @override
  String get e7SharedDismissThisRequest => '¿Rechazar esta petición?';

  @override
  String get e7SharedTheAgentContinuesWithoutYourAnswers =>
      'El agente continúa sin tus respuestas.';

  @override
  String get e7SharedAskedByAnMCPServer => 'Pregunta un servidor MCP';

  @override
  String get e7SharedAskedByTheAgentInThisSession =>
      'La pregunta la hace el agente en esta conversación';

  @override
  String get e7SharedInputRequested => 'Se necesita tu respuesta';

  @override
  String get e7SharedOther => 'Otra…';

  @override
  String get e7SharedYourAnswer => 'Tu respuesta';

  @override
  String get e7SharedAddYourOwn => 'Agrega tu propia opción';

  @override
  String get e7SharedAddAnswer => 'Agregar respuesta';

  @override
  String get e7SharedThisServerSentALinkThisApp =>
      'Este servidor envió un enlace que esta app no abrirá.';

  @override
  String get e7SharedSendAnswers => 'Enviar respuestas';

  @override
  String e7SharedDetail307(int step) {
    return 'Paso $step de 3';
  }

  @override
  String e7SharedDetail385(String count, String limit) {
    return '$count de $limit tokens';
  }

  @override
  String e7SharedDetail386(String count) {
    return '$count tokens · límite no disponible';
  }

  @override
  String e7SharedDetail429(String destination) {
    return '¿Mover la conversación a $destination?';
  }

  @override
  String e7SharedDetail514(String error) {
    return 'Actualización fallida: $error';
  }

  @override
  String e7SharedDetail714(int count) {
    return 'Debe tener al menos $count caracteres';
  }

  @override
  String e7SharedDetail715(int count) {
    return 'Debe tener a lo sumo $count caracteres';
  }

  @override
  String e7SharedDetail721(String minimum, String maximum) {
    return 'Debe estar entre $minimum y $maximum';
  }

  @override
  String e7SharedDetail722(String minimum) {
    return 'Debe ser al menos $minimum';
  }

  @override
  String e7SharedDetail723(String maximum) {
    return 'Debe ser a lo sumo $maximum';
  }

  @override
  String e7SharedDetail726(int count) {
    return 'Elige a lo sumo $count';
  }

  @override
  String e7SharedDetail753(int minimum, int maximum) {
    return 'Elige $minimum–$maximum';
  }

  @override
  String e7SharedDetail754(int count) {
    return 'Elige al menos $count';
  }

  @override
  String e7SharedDetail755(int count) {
    return 'Elige hasta $count';
  }

  @override
  String e7SharedDetail756(String range, int count) {
    return '$range · $count seleccionados';
  }

  @override
  String e7SharedDetail764(String host) {
    return 'Abre $host en tu navegador';
  }

  @override
  String e7SharedDetail765(String field) {
    return 'Este servidor envió un tipo de campo que esta app no entiende (“$field”).';
  }

  @override
  String get e7LocaleUiLanguage => 'Idioma';

  @override
  String get e7LocaleUiEnglish => 'English';

  @override
  String get e7LocaleUiArabic => 'العربية';

  @override
  String get e7LocaleUiSystem => 'Usar el idioma del sistema';

  @override
  String get e7LocaleUiClose => 'Cerrar';

  @override
  String get e7LocaleUiDescription =>
      'Elige el idioma de toda la aplicación. Los mensajes del servidor y tu texto se muestran tal como están.';

  @override
  String get e7LocaleUiSaving => 'Guardando el idioma…';

  @override
  String get e7LocaleUiSaveFailed =>
      'No se pudo guardar el idioma. Sigue activa tu elección anterior. Elige un idioma para intentarlo de nuevo.';

  @override
  String get e7LocaleUiNewSession => 'Nueva conversación';

  @override
  String get e7LocaleUiNewSessionHint =>
      'Empieza una conversación en el proyecto activo';

  @override
  String get e7LocaleUiWorkspaceHint => 'Cada conversación, entre proyectos';

  @override
  String get e7LocaleUiFilesHint =>
      'Archivos, cambios, terminal y más herramientas del proyecto';

  @override
  String get e7LocaleUiMoreHint =>
      'Modelos, proveedores, notificaciones, ajustes';

  @override
  String get e7LocaleUiSettings => 'Ajustes';

  @override
  String get e7LocaleUiKeyboardShortcuts => 'Atajos de teclado';

  @override
  String get e7LocaleUiRefreshSessions => 'Actualizar conversaciones';

  @override
  String get e7LocaleUiDiagnostics => 'Diagnóstico';

  @override
  String get e7LocaleUiDiagnosticsHint =>
      'Errores recientes y detalle de conexión';

  @override
  String get e7LocaleUiCommandLauncher => 'Lanzador de comandos';

  @override
  String get e7LocaleUiFindSurface => 'Buscar en esta pantalla';

  @override
  String get e7LocaleUiDestinations => 'Cambiar pestaña';

  @override
  String get e7LocaleUiTerminal => 'Terminal';

  @override
  String get e7LocaleUiCloseScreen => 'Cerrar esta pantalla';

  @override
  String get e7LocaleUiSendPrompt => 'Enviar el mensaje';

  @override
  String get e7LocaleUiCopyTranscript =>
      'Copiar el texto de transcripción seleccionado';

  @override
  String get e7LocaleUiRecentModel =>
      'Modelo de vida reciente siguiente / anterior de esta conversación';

  @override
  String get e7LocaleUiThisList => 'Esta lista';

  @override
  String get e7LocaleUiCloseOverlay =>
      'Cerrar una vista de fondo, un diálogo o un menú';

  @override
  String get e7LocaleUiContextActions =>
      'Acciones de mensaje, archivo y conversación';

  @override
  String get e7LocaleUiContextKeys => 'Clic derecho / Shift + F10 / Menú';

  @override
  String get e7LocaleUiConnectionChanged => 'El servidor cambió.';

  @override
  String get e7AppearanceFollowAndroid => 'Seguir Android';

  @override
  String get e7AppearanceFollowSystem => 'Seguir el sistema';

  @override
  String get e7AppearanceLight => 'Claro';

  @override
  String get e7AppearanceDark => 'Oscuro';

  @override
  String get e7AppearanceFollowPhoneDescription =>
      'Coincide con el ajuste claro u oscuro actual de este teléfono';

  @override
  String get e7AppearanceFollowDeviceDescription =>
      'Coincide con el ajuste claro u oscuro actual de este dispositivo';

  @override
  String get e7AppearanceLightDescription => 'Usa el tema claro editorial';

  @override
  String get e7AppearanceDarkDescription => 'Usa el tema tenue concentrado';

  @override
  String get e7AppearanceTitle => 'Apariencia';

  @override
  String get e7AppearancePreviewHint =>
      'Primero la vista previa. Tu apariencia solo cambia cuando la aplicas.';

  @override
  String get e7AppearanceDynamicUnavailable =>
      'Los colores de Material You no están disponibles en este dispositivo.';

  @override
  String e7AppearanceUsesMode(String mode) {
    return 'Tu ajuste de claro u oscuro sigue siendo $mode.';
  }

  @override
  String get e7AppearanceSaveFailed =>
      'No se pudo guardar la apariencia. Tu ajuste anterior no cambió. Inténtalo de nuevo.';

  @override
  String get e7AppearanceSaving => 'Guardando…';

  @override
  String get e7AppearanceApply => 'Aplicar';

  @override
  String get e7AppearanceCurrent => 'Aspecto actual';

  @override
  String get e7AppearanceClose => 'Cerrar';

  @override
  String get e7AppearancePreviewTitle => 'Texto y controles';

  @override
  String get e7AppearancePreviewBody =>
      'Mira cómo se juntan la lectura, el código y las acciones seleccionadas.';

  @override
  String get e7AppearanceSelection => 'Opción seleccionada';

  @override
  String get e7AppearanceTryControl => 'Prueba un control';

  @override
  String get e7AppearanceSampleHint =>
      'Los controles de muestra solo cambian esta vista previa.';

  @override
  String get e7SettingsUi1 => 'Servidor';

  @override
  String get e7SettingsUi8 => 'Desconectar';

  @override
  String get e7SettingsUi9 => 'Servidor OpenCode';

  @override
  String get e7SettingsUi11 => 'Revisando la salud del servidor…';

  @override
  String get e7SettingsUi12 => 'Detenido por Android';

  @override
  String get e7SettingsUi14 => 'Activado · en ejecución';

  @override
  String get e7SettingsUi15 => 'Activado · iniciando';

  @override
  String get e7SettingsUi16 => 'este servidor';

  @override
  String get e7SettingsUi17 => 'desconocido';

  @override
  String get e7SettingsUi18 => 'OpenCode está reconectando.';

  @override
  String get e7SettingsUi19 => 'OpenCode está reconectando. Reintenta.';

  @override
  String get e7SettingsUi22 => 'Android no activó el modo en segundo plano.';

  @override
  String get e7SettingsUi23 => 'Android detuvo la conexión en vivo';

  @override
  String get e7SettingsUi24 =>
      'Se agotó su límite diario de trabajo de sincronización de datos en segundo plano, así que el modo en vivo se desactivó. Vuelve a activarlo para reconectar; el límite se restablece en 24 horas.';

  @override
  String get e7SettingsUi25 => 'Mantener la conexión en segundo plano';

  @override
  String get e7SettingsUi26 =>
      'Mantiene las ejecuciones actualizadas con la app cerrada y te avisa cuando una te necesita. Usa más batería y muestra una notificación permanente.';

  @override
  String get e7SettingsUi27 =>
      'Acceso sin restricciones a la batería permitido';

  @override
  String get e7SettingsUi28 => 'Permitir acceso sin restricciones a la batería';

  @override
  String get e7SettingsUi29 =>
      'Android aún puede aplicar su límite de tiempo para servicios en primer plano.';

  @override
  String get e7SettingsUi30 =>
      'Opcional. Ayuda a conservar la conexión en vivo durante Doze. Android 15+ limita el trabajo de sincronización de datos en segundo plano a seis horas cada 24 horas.';

  @override
  String get e7SettingsUi31 => 'Detenido por Android: toca para reiniciar';

  @override
  String get e7SettingsUi32 => 'En ejecución ahora';

  @override
  String get e7SettingsUi34 =>
      'Android lo detiene tras 6 horas al día. La app te avisará cuando lo haga.';

  @override
  String get e7SettingsUi35 => 'Shell por defecto';

  @override
  String get e7SettingsUi36 =>
      'La usan las terminales nuevas y los comandos shell compatibles en este servidor de OpenCode.';

  @override
  String get e7SettingsUi37 =>
      'Solo de terminal; OpenCode usa un suplente compatible para las herramientas shell.';

  @override
  String get e7SettingsUi39 => 'Automático (por defecto del servidor)';

  @override
  String get e7SettingsUi41 => 'Cargando shells desde OpenCode…';

  @override
  String get e7SettingsUi46 => 'Reiniciar OpenCode en su host';

  @override
  String get e7SettingsUi48 => '¿Actualizar el OpenCode remoto?';

  @override
  String get e7SettingsUi49 =>
      'El servidor activo cambió antes de que terminara la actualización';

  @override
  String get e7SettingsUi52 => 'la versión anterior';

  @override
  String get e7SettingsUi53 => 'una versión desconocida';

  @override
  String get e7SettingsUi56 => 'No conectado';

  @override
  String get e7SettingsUi57 => 'Revisar la salud del servidor';

  @override
  String get e7SettingsUi58 => 'Preguntando al servidor cómo está';

  @override
  String get e7SettingsUi59 => 'Servidor sano';

  @override
  String get e7SettingsUi60 => 'Salud no disponible';

  @override
  String get e7SettingsUi62 => 'No hay contraseña de servidor guardada';

  @override
  String get e7SettingsUi65 => 'Configurar OpenCode 2';

  @override
  String get e7SettingsUi66 =>
      'Mantén OpenCode funcionando después de cerrar la terminal.';

  @override
  String get e7SettingsUi67 => 'Actualizaciones del servidor';

  @override
  String get e7SettingsUi68 =>
      'Actualizar desde la máquina que corre el servidor';

  @override
  String get e7SettingsUi69 => 'Claro u oscuro';

  @override
  String get e7SettingsUi70 => 'Tema';

  @override
  String get e7SettingsUi74 => 'Acciones siempre permitidas';

  @override
  String get e7SettingsUi76 => 'En este dispositivo';

  @override
  String get e7SettingsUi77 => 'Almacenamiento usado';

  @override
  String get e7SettingsUi78 => 'Borrar mensajes en cola';

  @override
  String get e7SettingsUi79 => 'No hay nada pendiente de enviar';

  @override
  String get e7SettingsUi80 => '¿Eliminar los mensajes en cola?';

  @override
  String get e7SettingsUi81 => 'Mensajes en cola eliminados';

  @override
  String get e7SettingsUi82 =>
      'No se pudieron eliminar los mensajes en cola. Revisa el almacenamiento del dispositivo e inténtalo de nuevo.';

  @override
  String get e7SettingsUi83 => 'Borrar borradores';

  @override
  String get e7SettingsUi84 => 'No hay texto del editor guardado';

  @override
  String get e7SettingsUi85 => '¿Eliminar los borradores?';

  @override
  String get e7SettingsUi86 => 'Borradores eliminados';

  @override
  String get e7SettingsUi87 =>
      'No se pudieron eliminar los borradores. Revisa el almacenamiento del dispositivo e inténtalo de nuevo.';

  @override
  String get e7SettingsUi88 => 'Diagnóstico de la app';

  @override
  String get e7SettingsUi92 => 'Privacidad y uso de datos';

  @override
  String get e7SettingsUi93 =>
      'Servidores, proveedores, voz, archivos y actualizaciones';

  @override
  String get e7SettingsUi94 => 'Licencias y procedencia de la voz';

  @override
  String get e7SettingsUi96 => 'Acerca de y avisos de código abierto';

  @override
  String e7SettingsDisconnectTitle(String server) {
    return '¿Desconectar de $server?';
  }

  @override
  String e7SettingsVersion(String version) {
    return 'Versión $version';
  }

  @override
  String get e7AppearancePackOpencode => 'Verde de terminal, el predeterminado';

  @override
  String get e7AppearancePackCatppuccin =>
      'Mocha y Latte, guiados por el malva';

  @override
  String get e7AppearancePackGruvbox => 'Retro cálido, guiado por el naranja';

  @override
  String get e7AppearancePackSolarized =>
      'La paleta dual clásica, guiada por el azul';

  @override
  String get e7AppearancePackDynamic =>
      'Los colores Material You de este teléfono';

  @override
  String e7SettingsStorageSummary(
    String total,
    int queued,
    String queueBytes,
    int drafts,
    String draftBytes,
    int days,
  ) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: '$queued mensajes en cola',
      one: '1 mensaje en cola',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: '$drafts borradores',
      one: '1 borrador',
    );
    return '$total de trabajo sin enviar: $_temp0 ($queueBytes) y $_temp1 ($draftBytes). Los mensajes en cola se descartan tras $days días.';
  }

  @override
  String e7SettingsQueueDeleteSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Elimina los $count mensajes sin enviar y sus adjuntos, de todos los servidores',
      one:
          'Elimina el mensaje sin enviar y sus adjuntos, de todos los servidores',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsQueueDeleteBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Esto elimina $count mensajes sin enviar y sus adjuntos, de todos los servidores. Nunca se enviarán. No se ve afectado nada en el servidor.',
      one:
          'Esto elimina el mensaje sin enviar y sus adjuntos, de todos los servidores. Nunca se enviará. No se ve afectado nada en el servidor.',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsDraftDeleteSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Elimina el texto del editor guardado para $count conversaciones',
      one: 'Elimina el texto del editor guardado para 1 conversación',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsDraftDeleteBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Esto elimina el texto del editor guardado para $count conversaciones. No se ve afectado nada en el servidor.',
      one:
          'Esto elimina el texto del editor guardado para 1 conversación. No se ve afectado nada en el servidor.',
    );
    return '$_temp0';
  }

  @override
  String e7SettingsRestartBody(String version, String current) {
    return 'OpenCode $version está instalado, pero el servidor sigue corriendo $current. Reinícialo en su computadora de la misma forma en que lo iniciaste; la app lo revisa otra vez.';
  }

  @override
  String e7SettingsInstallVersion(String version) {
    return 'Instalar $version';
  }

  @override
  String e7SettingsInstalledVersion(String version) {
    return 'OpenCode $version instalado. Reinicia el proceso del servidor para usarlo.';
  }

  @override
  String e7SettingsRestartVersion(String version) {
    return 'Reiniciar OpenCode para usar $version';
  }

  @override
  String e7SettingsRetryError(String error) {
    return '$error Toca para reintentar.';
  }

  @override
  String e7SettingsUpdateVersion(String version) {
    return 'Actualizar OpenCode a $version';
  }

  @override
  String get e7SettingsDetailUi17 => 'Privacidad';

  @override
  String get e7SettingsDetailUi18 => 'Código abierto';

  @override
  String get e7SettingsDetailUi19 => 'Acerca de esta compilación';

  @override
  String get e7SettingsDetailUi22 =>
      'Cliente móvil para un servidor de OpenCode. El reconocimiento de voz corre localmente después de descargas opcionales de modelos.';

  @override
  String get e7SettingsDetailUi23 =>
      'Cliente de escritorio para un servidor de OpenCode.';

  @override
  String get e7SettingsDetailUi25 => 'Incluido';

  @override
  String get e7SettingsDetailUi26 => 'Habilidades';

  @override
  String get e7SettingsDetailUi27 => 'Referencias';

  @override
  String get e7SettingsAlphaBody => 'OpenCode Mobile es una app de Android.';

  @override
  String get e7SettingsNonAffiliation =>
      'OpenCode Mobile es un proyecto comunitario independiente. No está construido, mantenido, respaldado por el equipo oficial de OpenCode, ni afiliado a este.';

  @override
  String e7SettingsDiagnosticOccurrences(int count) {
    return '$count apariciones';
  }

  @override
  String get e7ProjectProjectsReconnect =>
      'OpenCode está reconectando. Reintenta en un momento.';

  @override
  String e7ProjectProjectRenameFailed(String error) {
    return 'No se pudo renombrar el proyecto: $error';
  }

  @override
  String get e7ProjectProjectDefaultDirectory =>
      'El directorio por defecto del servidor';

  @override
  String get e7ProjectProjectSwitchUnavailableDetail =>
      'Este servidor conserva la carpeta configurada para las conversaciones. Empieza una conversación nueva desde Trabajo para continuar.';

  @override
  String get e7ProjectProjectsTitle => 'Proyectos';

  @override
  String get e7ProjectProjectsRefresh => 'Actualizar los proyectos';

  @override
  String get e7ProjectProjectsSearch => 'Buscar proyectos o rutas';

  @override
  String get e7ProjectProjectsOpened => 'Proyectos abiertos';

  @override
  String get e7ProjectProjectsEmpty => 'No hay proyectos abiertos';

  @override
  String get e7ProjectProjectsEmptyDetail =>
      'Los proyectos que abras o crees aparecerán aquí.';

  @override
  String get e7ProjectProjectsRefreshFailed =>
      'Falló la actualización de proyectos';

  @override
  String e7ProjectProjectWorktrees(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count worktrees',
      one: '1 worktree',
    );
    return '$_temp0';
  }

  @override
  String e7ProjectProjectRenameAction(String name) {
    return 'Renombrar $name';
  }

  @override
  String get e7ProjectProjectRenameTitle => 'Renombrar el proyecto';

  @override
  String get e7ProjectProjectNameLabel => 'Nombre del proyecto';

  @override
  String get e7ProjectProjectNameHint =>
      'Vacía el nombre para usar el nombre de la carpeta del proyecto.';

  @override
  String get e7ProjectProjectSave => 'Guardar';

  @override
  String get e7ProjectMonitorUnsupported =>
      'La atención en segundo plano no está disponible para este servidor. Abre la conversación para revisar las solicitudes actuales.';

  @override
  String get readerUiDisconnected => 'El servidor no está conectado.';

  @override
  String get readerUiIndicatorsUnavailable =>
      'Los indicadores de cambios en archivos no están disponibles en este servidor.';

  @override
  String get readerUiIndicatorsFailed =>
      'No se pudieron actualizar los indicadores de cambios en archivos.';

  @override
  String get readerUiReconnecting => 'OpenCode se está reconectando.';

  @override
  String get readerUiCommentAdded =>
      'Comentario de revisión agregado. Vuelve a la conversación para continuar.';

  @override
  String get readerUiCommentCopied =>
      'Comentario de revisión copiado. Pégalo en una conversación.';

  @override
  String get readerUiSearchSymbols => 'Buscar símbolos';

  @override
  String get readerUiSearchFiles => 'Buscar archivos';

  @override
  String get readerUiSelectFile => 'Selecciona un archivo para previsualizarlo';

  @override
  String get readerUiEmptyFolder => 'La carpeta está vacía';

  @override
  String get readerUiNoFiles => 'No se encontraron archivos';

  @override
  String get readerUiPullRefresh =>
      'Desliza hacia abajo para actualizar esta carpeta.';

  @override
  String get readerUiTryFileName => 'Prueba con otro nombre de archivo.';

  @override
  String get readerUiAttachPrompt => 'Adjuntar al mensaje';

  @override
  String get readerUiOpenReview => 'Abrir en Revisión';

  @override
  String get readerUiCopyPath => 'Copiar ruta';

  @override
  String get readerUiWorkspaceSymbols => 'Buscar símbolos del proyecto';

  @override
  String get readerUiSymbolsHint =>
      'Encuentra clases, funciones, métodos y variables por nombre.';

  @override
  String get readerUiNoSymbols => 'No se encontraron símbolos';

  @override
  String get readerUiSymbolsUnavailable =>
      'Prueba con otro nombre. Algunos servicios de lenguaje no admiten la búsqueda de símbolos en todo el proyecto.';

  @override
  String get readerUiFiles => 'Archivos';

  @override
  String get readerUiSymbols => 'Símbolos';

  @override
  String get readerUiChanges => 'Cambios';

  @override
  String get readerUiRefreshChanges => 'Actualizar los cambios';

  @override
  String get readerUiEntireChange => 'Cambio del archivo completo';

  @override
  String get readerUiWorkingTree => 'Sin comprometer';

  @override
  String get readerUiSessionScopeHint =>
      'Archivos que cambió esta conversación.';

  @override
  String get readerUiWorkingScopeHint =>
      'Todo lo aún no comprometido, lo cambie quien lo cambie.';

  @override
  String get readerUiBranchScopeHint =>
      'Todo lo de esta rama, comparada con la rama principal.';

  @override
  String get readerUiSaveDevice => 'Guardar en el dispositivo';

  @override
  String get readerUiPreviewUnavailable => 'Vista previa no disponible';

  @override
  String get readerUiRendered => 'Renderizado';

  @override
  String get readerUiRaw => 'En bruto';

  @override
  String get readerUiSaveFailed =>
      'No se pudieron guardar las preferencias del lector. Inténtalo de nuevo.';

  @override
  String get readerUiSourceFirst => 'Código fuente primero';

  @override
  String get readerUiServerOrder => 'Orden predeterminado';

  @override
  String readerUiLine(int number) {
    return 'línea $number';
  }

  @override
  String readerUiReferenceAdded(String label) {
    return '$label agregado al mensaje';
  }

  @override
  String readerUiReferenceDuplicate(String label) {
    return '$label ya está en el mensaje';
  }

  @override
  String readerUiReferenceFull(int count) {
    return 'El mensaje ya tiene $count referencias';
  }

  @override
  String readerUiAttached(String name) {
    return '$name adjuntado.';
  }

  @override
  String readerUiChangedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos modificados',
      one: '$count archivo modificado',
    );
    return '$_temp0';
  }

  @override
  String readerUiAttachedReturn(String name) {
    return '$name adjuntado. Vuelve a la conversación para agregar tu comentario.';
  }

  @override
  String readerUiSaveNamed(String name) {
    return 'Guardar $name';
  }

  @override
  String readerUiSavedDevice(String name) {
    return '$name se guardó en tu dispositivo.';
  }

  @override
  String readerUiPathLine(String path, int line) {
    return '$path · Línea $line';
  }

  @override
  String readerUiOnPrompt(int count) {
    return '$count en el mensaje';
  }

  @override
  String readerUiNewLines(String label) {
    return 'nuevas $label';
  }

  @override
  String readerUiOldLines(String label) {
    return 'antiguas $label';
  }

  @override
  String readerUiLineRange(int first, int last) {
    return 'líneas $first–$last';
  }

  @override
  String readerUiReviewPrompt(String path) {
    return 'Revisar `$path`';
  }

  @override
  String readerUiSaved(String name) {
    return '$name guardado.';
  }

  @override
  String get readerUiSymbolFile => 'Archivo';

  @override
  String get readerUiSymbolModule => 'Módulo';

  @override
  String get readerUiSymbolNamespace => 'Espacio de nombres';

  @override
  String get readerUiSymbolPackage => 'Paquete';

  @override
  String get readerUiSymbolClass => 'Clase';

  @override
  String get readerUiSymbolMethod => 'Método';

  @override
  String get readerUiSymbolProperty => 'Propiedad';

  @override
  String get readerUiSymbolField => 'Campo';

  @override
  String get readerUiSymbolConstructor => 'Constructor';

  @override
  String get readerUiSymbolEnum => 'Enum';

  @override
  String get readerUiSymbolInterface => 'Interfaz';

  @override
  String get readerUiSymbolFunction => 'Función';

  @override
  String get readerUiSymbolVariable => 'Variable';

  @override
  String get readerUiSymbolConstant => 'Constante';

  @override
  String get readerUiSymbolEnummember => 'Miembro de enum';

  @override
  String get readerUiSymbolStruct => 'Struct';

  @override
  String get readerUiSymbolEvent => 'Evento';

  @override
  String get readerUiSymbolOperator => 'Operador';

  @override
  String get readerUiSymbolTypeparameter => 'Parámetro de tipo';

  @override
  String get readerUiSymbolSymbol => 'Símbolo';

  @override
  String get readerUiAdded => 'Agregado';

  @override
  String get readerUiDeleted => 'Eliminado';

  @override
  String get readerUiModified => 'Modificado';

  @override
  String get readerUiChanged => 'Cambiado';

  @override
  String get readerUiSession => 'Esta conversación';

  @override
  String get readerUiBranch => 'Rama completa';

  @override
  String get readerUiAttachmentMissing =>
      'El contenido del adjunto no viene incluido en este mensaje.';

  @override
  String get readerUiRemoteAttachment =>
      'Las vistas previas de adjuntos remotos no están disponibles.';

  @override
  String get readerUiAttachmentInvalid =>
      'No se pudieron descifrar los datos del adjunto.';

  @override
  String get e7WorkspaceDisconnected => 'El servidor no está conectado.';

  @override
  String get e7WorkspaceNeedsYou => 'Requiere tu atención';

  @override
  String get e7WorkspaceStopSharing => 'Dejar de compartir';

  @override
  String get e7WorkspaceBackExit => 'Pulsa «Atrás» otra vez para salir';

  @override
  String get e7WorkspaceConnected => 'Conectado';

  @override
  String get e7WorkspaceConnecting => 'Conectando';

  @override
  String get e7WorkspaceOffline => 'Sin conexión';

  @override
  String get e7WorkspaceReconnectingAgain =>
      'OpenCode está reconectando. Reintenta.';

  @override
  String get e7WorkspaceDismissRequest => '¿Descartar esta solicitud?';

  @override
  String get e7WorkspaceDismissDetail =>
      'OpenCode continuará sin respuestas a estas preguntas.';

  @override
  String get e7WorkspaceNeedsInput => 'OpenCode necesita tu respuesta';

  @override
  String activityAgentNeedsInput(String agent) {
    return '$agent necesita tu respuesta';
  }

  @override
  String get e7WorkspaceSendAnswers => 'Enviar respuestas';

  @override
  String get e7WorkspaceReferenceRetry =>
      'Referencia de conversación no disponible. Actualiza y reintenta.';

  @override
  String get e7WorkspaceLocationRetry =>
      'El proyecto de la conversación no está disponible. Actualiza y reintenta.';

  @override
  String get e7WorkspaceLocationChangedReturn =>
      'El proyecto de la conversación cambió. Vuelve o actualiza y reintenta.';

  @override
  String get e7WorkspaceLocationChangedRetry =>
      'El proyecto de la conversación cambió. Actualiza y reintenta.';

  @override
  String get e7WorkspaceReferenceUnavailable =>
      'Referencia de conversación no disponible.';

  @override
  String get e7WorkspacePaginationStuck =>
      'La paginación de la conversación no pudo avanzar. Actualiza la lista para continuar.';

  @override
  String e7WorkspaceSessionId(String id) {
    return 'Conversación $id';
  }

  @override
  String get e7WorkspaceUnknownProject => 'Proyecto desconocido';

  @override
  String get e7WorkspaceJustNow => 'Hace un momento';

  @override
  String e7WorkspaceMinutesAgo(int count) {
    return 'hace $count min';
  }

  @override
  String e7WorkspaceHoursAgo(int count) {
    return 'hace $count h';
  }

  @override
  String e7WorkspaceDaysAgo(int count) {
    return 'hace $count d';
  }

  @override
  String e7WorkspaceFileCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos',
      one: '1 archivo',
    );
    return '$_temp0';
  }

  @override
  String get chatUiAllMatchingRequests =>
      '(todas las solicitudes coincidentes)';

  @override
  String get chatUiNoOutput => '(sin salida)';

  @override
  String get chatUiNoResult => '(sin resultado)';

  @override
  String get chatUi1ReferenceIsAddedAsTextWhen =>
      '1 referencia se añade como texto al enviar. No se guarda con tu borrador.';

  @override
  String get chatUiAddAnOpenCodeProjectReferenceToThis =>
      'Agrega una referencia de proyecto de OpenCode a este mensaje';

  @override
  String get chatUiAddAnImageOrFileToThe =>
      'Agrega una imagen o un archivo al mensaje';

  @override
  String get chatUiAgent => 'Agente';

  @override
  String get chatUiAlreadyAnsweredElsewhere => 'Ya se respondió en otra parte';

  @override
  String get chatUiAlreadyDelivered => 'Ya entregado';

  @override
  String get chatUiAlwaysAllow => 'Permitir siempre';

  @override
  String get chatUiAlwaysAllowWouldAlsoCover =>
      '«Permitir siempre» también cubriría';

  @override
  String get chatUiAnswerWasCutOffByTheLength =>
      'La respuesta se cortó por el límite de longitud';

  @override
  String get chatUiAnyoneWithTheLinkCanViewThis =>
      'Cualquiera con el enlace puede ver esta conversación y su contexto compartido. No compartas conversaciones que contengan secretos, credenciales o archivos privados.';

  @override
  String get chatUiAppDiagnostics => 'Diagnóstico de la app';

  @override
  String get chatUiAppearance => 'Aspecto';

  @override
  String get chatUiApplyPatch => 'Aplicar parche';

  @override
  String get chatUiAskOpenCode => 'Pregunta a OpenCode…';

  @override
  String get chatUiAttachFile => 'Adjuntar archivo';

  @override
  String get chatUiAttachToPrompt => 'Adjuntar al mensaje';

  @override
  String get chatUiAttachment => 'Adjunto';

  @override
  String get chatUiAttachmentLimitReached => 'Límite de adjuntos alcanzado';

  @override
  String get chatUiAttachmentsMustTotalNoMoreThan20 =>
      'Los adjuntos deben sumar como máximo 20 MB.';

  @override
  String get chatUiAvailableWhenTheCurrentRunFinishes =>
      'Disponible cuando termine la ejecución actual';

  @override
  String get chatUiBrowseProjectAndGlobalSkills =>
      'Explorar las skills del proyecto y globales';

  @override
  String get chatUiBrowsePreviewDownloadAndAttachProjectFiles =>
      'Explora, previsualiza, descarga y adjunta archivos del proyecto';

  @override
  String get chatUiCancelAndReturnToTheComposer =>
      'Cancelar y volver al cuadro de mensaje';

  @override
  String get chatUiCancelMessage => 'Cancelar mensaje';

  @override
  String get chatUiCancelThisPendingMessage =>
      '¿Cancelar este mensaje pendiente?';

  @override
  String get chatUiChangeTheActiveOpenCodeConsoleOrganization =>
      'Cambiar la organización activa de OpenCode Console';

  @override
  String get chatUiChangeTheTitleShownInTheSession =>
      'Cambiar el título que se muestra en la lista de conversaciones';

  @override
  String get chatUiChangeThisSessionSExperimentalWorkspace =>
      'Cambiar el entorno en la nube experimental de esta conversación';

  @override
  String get chatUiChangedFile => 'Archivo cambiado';

  @override
  String get chatUiChanges => 'Cambios';

  @override
  String get chatUiChooseAServerModelByProviderAnd =>
      'Elegir un modelo del servidor por proveedor y capacidad';

  @override
  String get chatUiChooseAnotherModelInThePickerTo =>
      'Elige otro modelo en el selector para crear tu lista de recientes.';

  @override
  String get chatUiChooseModel => 'Elegir modelo';

  @override
  String get chatUiChooseTheActiveOpenCodeAgent =>
      'Elegir el agente de OpenCode activo';

  @override
  String chatUiChooseAgentMode(String agent) {
    return 'Elige cómo trabaja $agent: sus modos';
  }

  @override
  String get chatUiChooseTheCurrentModelVariantOrReasoning =>
      'Elegir la variante o el esfuerzo de razonamiento del modelo actual';

  @override
  String get chatUiCollapseReasoning => 'Colapsar el razonamiento';

  @override
  String get chatUiCommandMap => 'Mapa de comandos';

  @override
  String get chatUiCompactContext => 'Compactar el contexto';

  @override
  String get chatUiCompactSession => 'Compactar conversación';

  @override
  String get chatUiCompactingConversation => 'Compactando la conversación…';

  @override
  String get chatUiCompactionFailed => 'Falló la compactación';

  @override
  String get chatUiCompactionStarted => 'Compactación iniciada';

  @override
  String get chatUiCompose => 'Componer';

  @override
  String get chatUiConnectProvider => 'Conectar un proveedor';

  @override
  String get chatUiConnectionHealthServerVersionAndLiveMode =>
      'Salud de la conexión, versión del servidor y modo en vivo';

  @override
  String get chatUiContextAdded => 'Contexto agregado';

  @override
  String get chatUiContextCompacted =>
      'Los mensajes anteriores se resumieron para ahorrar espacio';

  @override
  String get chatUiCopiedPasteItIntoTheComposer =>
      'Copiado. Pégalo en el compositor';

  @override
  String get chatUiCopyMessageText => 'Copiar el texto del mensaje';

  @override
  String get chatUiCopyShareLink => 'Copiar enlace para compartir';

  @override
  String get chatUiCopyTheRenderedConversationAsMarkdown =>
      'Copiar la conversación renderizada como Markdown';

  @override
  String get chatUiCopyTranscript => 'Copiar la transcripción';

  @override
  String get chatUiCreateOrCopyAPublicSessionLink =>
      'Crear o copiar un enlace público de conversación';

  @override
  String get chatUiCurrentSession => 'Conversación actual';

  @override
  String get chatUiDelegate => 'Delegar';

  @override
  String get chatUiDelegateThisPrompt => 'Delegar este mensaje';

  @override
  String get chatUiDelegateThisPromptToAServerSubagent =>
      'Delega este mensaje a un sub-agente del servidor';

  @override
  String get chatUiDelegatedSession => 'Conversación delegada';

  @override
  String get chatUiDeleteMessage => 'Eliminar mensaje';

  @override
  String get chatUiDeleteThisMessage => '¿Eliminar este mensaje?';

  @override
  String get chatUiDetails => 'Detalles';

  @override
  String get chatUiDirectory => 'Directorio';

  @override
  String get chatUiDisableTheCurrentPublicSessionLink =>
      'Desactivar el enlace público de conversación actual';

  @override
  String get chatUiDiscardDraft => 'Descartar borrador';

  @override
  String get chatUiDiscardPromptChanges =>
      '¿Descartar los cambios del mensaje?';

  @override
  String get chatUiDiscardQueuedDraft => '¿Descartar el borrador en cola?';

  @override
  String get chatUiDismissPromptError => 'Descartar el error del mensaje';

  @override
  String get chatUiEachAttachmentMustBe10MBOr =>
      'Cada adjunto debe pesar 10 MB o menos.';

  @override
  String get chatUiEdit => 'Editar';

  @override
  String get chatUiEditDraft => 'Editar borrador';

  @override
  String get chatUiEditTheCurrentPromptInAFocused =>
      'Editar el mensaje actual en una vista a pantalla completa concentrada';

  @override
  String get chatUiErrorDetails => 'Detalles del error';

  @override
  String get chatUiExpandReasoning => 'Expandir el razonamiento';

  @override
  String get chatUiExportSessionTranscript =>
      'Exportar la transcripción de la conversación';

  @override
  String get chatUiExportTranscript => 'Exportar la transcripción';

  @override
  String get chatUiFetchPage => 'Traer página';

  @override
  String get chatUiFiles => 'Archivos';

  @override
  String get chatUiFilesAreUnavailableInThisPreview =>
      'Los archivos no están disponibles en esta vista previa.';

  @override
  String get chatUiFindACommandOrAction => 'Buscar un comando o una acción';

  @override
  String get chatUiFindAMessageJumpToItOr =>
      'Busca un mensaje, salta a él, o bifurca desde un mensaje';

  @override
  String get chatUiFindASubagent => 'Buscar un sub-agente';

  @override
  String get chatUiFindFiles => 'Buscar archivos';

  @override
  String get chatUiFindSessionsAcrossEveryOpenCodeProject =>
      'Buscar conversaciones en todos los proyectos de OpenCode';

  @override
  String get chatUiFollowAndroidOrChooseTheNativeLight =>
      'Sigue a Android o elige el tema nativo claro o oscuro';

  @override
  String get chatUiForkFromThisPrompt => 'Bifurcar desde este mensaje';

  @override
  String get chatUiForkSession => 'Bifurcar la conversación';

  @override
  String get chatUiGeneratedFile => 'Archivo generado';

  @override
  String get chatUiHideTimestamps => 'Ocultar marcas de tiempo';

  @override
  String get chatUiImageDataIsUnavailable =>
      'Los datos de la imagen no están disponibles.';

  @override
  String get chatUiInputRequested => 'Se necesita tu respuesta';

  @override
  String get chatUiInspectGitLanguageServicesAndFormattersFor =>
      'Revisar Git, servicios de lenguaje y formateadores de este proyecto';

  @override
  String get chatUiInspectMCPStatusAuthenticationAndResources =>
      'Revisar el estado de MCP, la autenticación y los recursos';

  @override
  String get chatUiInspectCurrentTokensCacheCostAndContext =>
      'Revisar los tokens actuales, la caché, el costo y el uso de contexto';

  @override
  String get chatUiInspectToolsCallableByTheActiveProvider =>
      'Revisar las herramientas llamables del proveedor y del modelo activos';

  @override
  String get chatUiItsTextReturnsToTheComposerAs =>
      'Su texto vuelve al cuadro de mensaje como borrador.';

  @override
  String get chatUiJumpAnywhereForkRestoresAPromptFor =>
      'Salta a cualquier parte. La bifurcación restaura un mensaje para editar.';

  @override
  String get chatUiKeepItPending => 'Mantenerlo pendiente';

  @override
  String get chatUiKeepItQueued => 'Mantenerlo en cola';

  @override
  String get chatUiLanguageServer => 'Servidor de lenguaje';

  @override
  String get chatUiList => 'Lista';

  @override
  String get chatUiLoadingSubagents => 'Cargando sub-agentes…';

  @override
  String get chatUiLongReasoningCollapsedInTheTranscript =>
      'Razonamiento largo contraído en la transcripción';

  @override
  String get chatUiMCPServers => 'Servidores MCP';

  @override
  String get chatUiManageProviderAndIntegrationAuthentication =>
      'Gestionar la autenticación de proveedores e integraciones';

  @override
  String get chatUiMessage => 'Mensaje';

  @override
  String get chatUiMessageTimeline => 'Cronología de mensajes';

  @override
  String get chatUiMessageTimestampsHidden =>
      'Marcas de hora de los mensajes ocultas';

  @override
  String get chatUiMessageTimestampsShown =>
      'Marcas de hora de los mensajes visibles';

  @override
  String get chatUiModel => 'Modelo';

  @override
  String get chatUiModelAndAgent => 'Modelo y agente';

  @override
  String get chatUiMoveSession => 'Mover la conversación';

  @override
  String get chatUiMoveThisSessionToAnotherProjectDirectory =>
      'Mover esta conversación a otro proyecto';

  @override
  String get chatUiMoved => 'Movido';

  @override
  String get chatUiNavigate => 'Navegar';

  @override
  String get chatUiNoAnswer => 'Sin respuesta';

  @override
  String get chatUiNoMatchingMessages => 'No hay mensajes coincidentes';

  @override
  String get chatUiNoShareLinkWasReturned =>
      'No se devolvió ningún enlace para compartir';

  @override
  String get chatUiNoSubagentsAvailableFromThisServer =>
      'Este servidor no ofrece sub-agentes';

  @override
  String get chatUiNotConnectedToTheServerRightNow =>
      'No conectado al servidor en este momento.';

  @override
  String get chatUiOpenParentSession => 'Abrir la conversación principal';

  @override
  String get chatUiOpenPersistentWorkspaceTerminals =>
      'Abrir terminales persistentes del proyecto';

  @override
  String get chatUiOpenProviders => 'Abrir proveedores';

  @override
  String get chatUiOpenSubagentSession => 'Abrir la conversación del subagente';

  @override
  String get chatUiOpenCodeCommandsAreUnavailableOffline =>
      'Los comandos de OpenCode no están disponibles sin conexión.';

  @override
  String get chatUiOpenCodeCouldNotCompleteThisPrompt =>
      'OpenCode no pudo completar este mensaje.';

  @override
  String get chatUiOpenCodeIsReconnecting => 'OpenCode está reconectando.';

  @override
  String chatUiAgentIsReconnecting(String agent) {
    return '$agent está reconectando.';
  }

  @override
  String chatUiAgentIsReconnectingTryAgainShortly(String agent) {
    return '$agent está reconectando. Reintenta en un momento.';
  }

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainShortly =>
      'OpenCode está reconectando. Reintenta en un momento.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgainWhenThe =>
      'OpenCode se está reconectando. Inténtalo de nuevo cuando el servidor esté en línea.';

  @override
  String get chatUiOpenCodeIsReconnectingTryAgain =>
      'OpenCode se está reconectando. Inténtalo de nuevo.';

  @override
  String get chatUiOpenCodeNeedsInput => 'OpenCode necesita tu respuesta';

  @override
  String get chatUiOpenCodeServerCommand => 'Comando del servidor OpenCode';

  @override
  String get chatUiOutputPruned => 'Salida recortada';

  @override
  String get chatUiPendingChange => 'Cambio pendiente';

  @override
  String get chatUiProjectFiles => 'Archivos del proyecto';

  @override
  String get chatUiProjectHealth => 'Salud del proyecto';

  @override
  String get chatUiProjectReference => 'Referencia de proyecto';

  @override
  String get chatUiProjectReferences => 'Referencias de proyecto';

  @override
  String get chatUiProjectsAndWorkspaces => 'Proyectos y árboles de trabajo';

  @override
  String get chatUiPromptEditor => 'Editor de mensajes';

  @override
  String get chatUiPromptFromParentAgent => 'Mensaje del agente madre';

  @override
  String get chatUiPromptTools => 'Herramientas del mensaje';

  @override
  String get chatUiQuestion => 'Pregunta';

  @override
  String get chatUiQuestions => 'Preguntas';

  @override
  String get chatUiQueuedRunsAfterThisTurn =>
      'En la fila · corre después de este turno';

  @override
  String get chatUiQueuedWillSendWhenReconnected =>
      'En cola: se enviará al reconectar';

  @override
  String get chatUiRead => 'Leer';

  @override
  String get chatUiReasoningExpandedInTheTranscript =>
      'Razonamiento expandido en la transcripción';

  @override
  String get chatUiRecordsAndTranscribesOnThisDevice =>
      'Graba y transcribe en este dispositivo';

  @override
  String get chatUiReferenceKeptForYourNextPromptCommands =>
      'Referencia conservada para tu próximo mensaje: los comandos no la incluyen.';

  @override
  String get chatUiReferencesKeptForYourNextPromptCommands =>
      'Referencias conservadas para tu próximo mensaje: los comandos no las incluyen.';

  @override
  String get chatUiReloadMessages => 'Actualizar mensajes';

  @override
  String get chatUiRename => 'Renombrar';

  @override
  String get chatUiRenameSession => 'Renombrar conversación';

  @override
  String get chatUiRestoreRevertedPrompt => 'Restaurar el mensaje revertido';

  @override
  String get chatUiRestoreTheCurrentlyRevertedSessionState =>
      'Restaurar el estado de conversación actualmente revertido';

  @override
  String get chatUiRetryLastPrompt => 'Reintentar el último mensaje';

  @override
  String get chatUiRevertLastPrompt => 'Deshacer el último mensaje';

  @override
  String get chatUiReviewCommentAddedToThePrompt =>
      'Comentario de revisión agregado al mensaje';

  @override
  String get chatUiReviewHandledAppErrorsAndSendA =>
      'Revisar los errores de la app gestionados y enviar un informe ofuscado';

  @override
  String get chatUiReviewTheActualDiffForThisSession =>
      'Revisar el diff real de esta conversación';

  @override
  String get chatUiRunOnYourComputer => 'Ejecutar en tu computadora';

  @override
  String get chatUiRunShellCommand => 'Ejecutar un comando shell';

  @override
  String get chatRunShellLabel => 'Comando';

  @override
  String get chatRunShellHint => 'npm test';

  @override
  String get chatRunShellHelper =>
      'El agente lo ejecuta en este proyecto, y su salida entra a la conversación.';

  @override
  String get chatRunShellEmpty => 'Escribe un comando para ejecutar.';

  @override
  String get chatRenameEmpty => 'Escribe un título.';

  @override
  String get chatUiSaveTheConversationAsAMarkdownFile =>
      'Guardar la conversación como archivo Markdown';

  @override
  String get chatUiScope => 'Ámbito';

  @override
  String get chatUiSearchMessages => 'Buscar mensajes';

  @override
  String get chatUiSearchMobileActionsAndServerProvidedCommands =>
      'Buscar acciones móviles y comandos que da el servidor';

  @override
  String get chatUiSearchText => 'Buscar texto';

  @override
  String get chatUiSelectAModelBeforeCompactingThisSession =>
      'Selecciona un modelo antes de compactar esta conversación.';

  @override
  String get chatUiServerCommands => 'Comandos del servidor';

  @override
  String get chatUiServerMessage => 'Mensaje del servidor';

  @override
  String get chatUiServerStatus => 'Estado del servidor';

  @override
  String get chatUiSessionChanges => 'Cambios de la conversación';

  @override
  String get chatUiSessionContext => 'Contexto de la conversación';

  @override
  String get chatUiSessionSharedCopyTheVisibleLinkManually =>
      'Conversación compartida. Copia manualmente el enlace visible.';

  @override
  String get chatUiShareLinkCopied => 'Enlace para compartir copiado';

  @override
  String get chatUiShareSession => 'Compartir conversación';

  @override
  String get chatUiShareThisSession => '¿Compartir esta conversación?';

  @override
  String get chatUiSharedAnyoneWithTheLinkCanView =>
      'Compartida: cualquiera con el enlace puede verla';

  @override
  String get chatUiShowAllSubagentSessions =>
      'Mostrar todas las conversaciones de subagentes';

  @override
  String get chatUiShowTimestamps => 'Mostrar marcas de tiempo';

  @override
  String get chatUiSkill => 'Skill ·';

  @override
  String get chatUiSkills => 'Skills';

  @override
  String get chatUiSlashCommandsAndAgents => 'Comandos con barra y agentes';

  @override
  String get chatUiStartACleanSessionInThisWorkspace =>
      'Empieza una conversación limpia en este proyecto';

  @override
  String get chatUiStopSharing => 'Dejar de compartir';

  @override
  String get chatUiSubagent => 'Sub-agente';

  @override
  String get chatUiSubagentFailed => 'El sub-agente falló.';

  @override
  String get chatUiSubagentWorking => 'Sub-agente trabajando…';

  @override
  String get chatUiSubagentsCouldNotBeLoaded =>
      'No se pudieron cargar los sub-agentes';

  @override
  String get chatUiSummarizeTheSessionUsingTheSelectedModel =>
      'Resumir la conversación con el modelo seleccionado';

  @override
  String get chatUiSwitchOrganization => 'Cambiar de organización';

  @override
  String get chatUiSwitchProjectDirectoryOrWorktree =>
      'Cambiar de proyecto, directorio o árbol de trabajo';

  @override
  String get chatUiSystemUpdate => 'Actualización del sistema';

  @override
  String get chatUiTellTheAgentWhyOrWhatTo =>
      'Dile al agente por qué, o qué hacer en su lugar (opcional)';

  @override
  String get chatUiThatMessageIsNoLongerInThis =>
      'Ese mensaje ya no está en esta conversación.';

  @override
  String get chatUiTheFileHasNoContentToAttach =>
      'El archivo no tiene contenido para adjuntar.';

  @override
  String get chatUiTheFileHasNoContentToSave =>
      'El archivo no tiene contenido para guardar.';

  @override
  String get chatUiTheFormOrProjectChangedReopenThe =>
      'El formulario o el proyecto cambió. Reabre la petición actual.';

  @override
  String get chatUiTheGeneratedFileIsNotAvailableFrom =>
      'El archivo generado no está disponible en este servidor.';

  @override
  String get chatUiTheMessageAndAllOfItsParts =>
      'El mensaje y todas sus partes se eliminan de forma permanente de la conversación, así que las próximas respuestas ya no los verán. Los cambios de archivos que hizo no se revierten.';

  @override
  String get chatUiTheServerReturnedEmptyImageData =>
      'El servidor devolvió datos de imagen vacíos.';

  @override
  String get chatUiThisDraftHasNotBeenSentTo =>
      'Este borrador aún no se ha enviado a OpenCode.';

  @override
  String get chatUiThisDraftIsTooLargeToQueue =>
      'Este borrador es demasiado grande para ponerlo en cola, o la cola está llena de borradores más recientes. Quita un adjunto o borra los mensajes en cola en Ajustes.';

  @override
  String get chatUiThisPromptCannotBeRestoredBecauseAn =>
      'No se puede restaurar este mensaje porque un adjunto no está disponible.';

  @override
  String get chatUiThisPromptCannotBeRetriedBecauseAn =>
      'No se puede reintentar este mensaje porque un adjunto no está disponible.';

  @override
  String get chatUiTimeline => 'Cronología';

  @override
  String get chatUiTimestampsUsage => 'Marcas de tiempo y consumo';

  @override
  String get chatUiTitle => 'Título';

  @override
  String get chatUiTodos => 'Tareas';

  @override
  String get chatUiToggleCreationTimesBesideTranscriptEntries =>
      'Ver u ocultar los tiempos de creación junto a las entradas de la transcripción';

  @override
  String get chatUiToggleLongReasoningDetailsAcrossTheTranscript =>
      'Ver u ocultar los detalles largos de razonamiento a lo largo de la transcripción';

  @override
  String get chatUiToolFailed => 'La herramienta falló.';

  @override
  String get chatUiToolsAndCapabilities => 'Herramientas y capacidades';

  @override
  String get chatUiTranscriptCopiedAsMarkdown =>
      'Transcripción copiada como Markdown';

  @override
  String get chatUiTranscriptDisplay => 'Presentación de la transcripción';

  @override
  String get chatUiTranscriptSaved => 'Transcripción guardada';

  @override
  String get chatUiVoiceConversationWasInterrupted =>
      'La conversación por voz se interrumpió.';

  @override
  String get chatUiVoiceInput => 'Entrada de voz';

  @override
  String get chatUiVoiceInputIsUnavailable =>
      'La entrada de voz no está disponible.';

  @override
  String get chatUiLoadTools => 'Cargar herramientas';

  @override
  String chatUiToolFrom(String tool, String server) {
    return '$tool · $server';
  }

  @override
  String get chatUiWebSearch => 'Búsqueda web';

  @override
  String get chatUiWrite => 'Escribir';

  @override
  String get chatUiWriteYourOpenCodePrompt =>
      'Escribe tu mensaje para OpenCode…';

  @override
  String get chatUiYou => 'Tú';

  @override
  String get chatUiYourOriginalComposerDraftAndAttachmentsWill =>
      'Tu borrador original del compositor y sus adjuntos se quedarán igual.';

  @override
  String get chatUiInThisChat => 'en esta conversación';

  @override
  String get chatUiNewFile => 'archivo nuevo';

  @override
  String chatUiQueuedWithEviction(Object detail) {
    return 'En cola: se enviará al reconectar. $detail';
  }

  @override
  String chatUiCommandUnavailable(Object command) {
    return '/$command no está disponible ahora mismo.';
  }

  @override
  String chatUiAttachmentCountLimit(Object count) {
    return 'Puedes adjuntar hasta $count archivos.';
  }

  @override
  String chatUiQueuedSent(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se enviaron $count mensajes en cola',
      one: 'Se envió 1 mensaje en cola',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaitingSuffix(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count borradores esperando a otros servidores',
      one: '1 borrador esperando a otros servidores',
    );
    return ' · $_temp0';
  }

  @override
  String chatUiNextTurnsModel(Object model) {
    return 'Los próximos turnos de esta conversación usan $model.';
  }

  @override
  String chatUiReferenceAlreadyAdded(Object name) {
    return '@$name ya está en el mensaje';
  }

  @override
  String chatUiFileAttached(Object filename) {
    return '$filename adjuntado. Agrega tu comentario.';
  }

  @override
  String chatUiSaveFile(Object filename) {
    return 'Guardar $filename';
  }

  @override
  String chatUiFileSaved(Object filename) {
    return '$filename se guardó en tu dispositivo.';
  }

  @override
  String chatUiDraftsQueued(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count borradores en cola; se enviarán al reconectar.',
      one: '1 borrador en cola; se enviará al reconectar.',
    );
    return '$_temp0';
  }

  @override
  String chatUiOtherDraftsWaiting(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count borradores esperando a otros servidores.',
      one: '1 borrador esperando a otros servidores.',
    );
    return '$_temp0';
  }

  @override
  String chatUiQuestionCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count preguntas',
      one: '1 pregunta',
    );
    return '$_temp0';
  }

  @override
  String chatUiPermissionNeeded(Object title) {
    return 'Se necesita permiso: $title';
  }

  @override
  String chatUiQuestionLabel(Object title) {
    return 'Pregunta: $title';
  }

  @override
  String chatUiQuestionsSummary(Object question, num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count preguntas',
      one: '1 pregunta',
    );
    return '$question · $_temp0';
  }

  @override
  String chatUiRateLimitRetry(Object attempt) {
    return 'Límite de velocidad alcanzado. Reintentando$attempt…';
  }

  @override
  String chatUiRateLimitCountdown(Object attempt, Object time) {
    return 'Límite de velocidad alcanzado. Reintentando$attempt en $time';
  }

  @override
  String chatUiReferencesAttachedNotice(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count referencias se añaden como texto al enviar. No se guardan con tu borrador.',
      one:
          '1 referencia se añade como texto al enviar. No se guarda con tu borrador.',
    );
    return '$_temp0';
  }

  @override
  String chatUiAttachedCount(Object count) {
    return '$count adjuntos';
  }

  @override
  String chatUiEarlierMessageCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes anteriores',
      one: '1 mensaje anterior',
    );
    return '$_temp0';
  }

  @override
  String chatUiTokenCount(Object count) {
    return '$count tok';
  }

  @override
  String chatUiPositionOfTotal(Object position, Object total) {
    return '$position de $total';
  }

  @override
  String chatUiSubagentCount(Object count) {
    return 'Conversación delegada · $count';
  }

  @override
  String chatUiToolsSummary(Object tools) {
    return 'Herramientas: $tools';
  }

  @override
  String chatUiFromLine(Object line) {
    return 'desde la línea $line';
  }

  @override
  String chatUiLineCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas',
      one: '1 línea',
    );
    return '$_temp0';
  }

  @override
  String chatUiLineRange(Object start, Object end) {
    return 'L$start–$end';
  }

  @override
  String chatUiEntryCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count entradas',
      one: '1 entrada',
    );
    return '$_temp0';
  }

  @override
  String chatUiFoundCount(Object count) {
    return '$count encontradas';
  }

  @override
  String chatUiMatchCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count coincidencias',
      one: '1 coincidencia',
    );
    return '$_temp0';
  }

  @override
  String chatUiFileCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos',
      one: '1 archivo',
    );
    return '$_temp0';
  }

  @override
  String chatUiProviderSearch(Object provider) {
    return 'búsqueda de $provider';
  }

  @override
  String chatUiResultCount(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count resultados',
      one: '1 resultado',
    );
    return '$_temp0';
  }

  @override
  String chatUiAnsweredCount(Object count) {
    return '$count respondidas';
  }

  @override
  String chatUiAskedCount(Object count) {
    return '$count formuladas';
  }

  @override
  String chatUiDurationMinutesSeconds(Object minutes, Object seconds) {
    return '${minutes}m ${seconds}s';
  }

  @override
  String chatUiDurationSeconds(Object seconds) {
    return '${seconds}s';
  }

  @override
  String chatUiFileLoadFailed(Object error) {
    return 'No se pudo cargar este archivo desde el servidor de OpenCode: $error';
  }

  @override
  String chatUiMoreEntries(Object total) {
    return '$total en total · hay más';
  }

  @override
  String chatUiEntryTotal(Object total) {
    return '$total entradas';
  }

  @override
  String chatUiAnsweredDetail(Object answer) {
    return 'Respondió: $answer';
  }

  @override
  String chatUiLoadingFile(Object filename) {
    return 'Cargando $filename';
  }

  @override
  String chatUiPreviewGeneratedImage(Object filename) {
    return 'Vista previa de la imagen generada $filename';
  }

  @override
  String chatUiParentSession(Object title) {
    return 'Primaria · $title';
  }

  @override
  String chatUiChooseOption(Object option) {
    return 'Elegir: $option';
  }

  @override
  String get chatUiMainSession => 'Conversación principal';

  @override
  String get chatUiTodo => 'Tarea pendiente';

  @override
  String get chatUiBackgroundResult => 'Resultado en segundo plano';

  @override
  String get chatUiBackgroundComplete => 'Completado';

  @override
  String get chatUiBackgroundError => 'Falló';

  @override
  String get chatUiBackgroundCancelled => 'Cancelado';

  @override
  String get chatUiResultSourceDetails => 'Detalles del mensaje del servidor';

  @override
  String get chatUiResultOpenChild => 'Abrir la conversación del subagente';

  @override
  String get chatUiNoResultText =>
      'El servidor no devolvió texto de resultado.';

  @override
  String get chatUiTimedOut => 'Tiempo agotado';

  @override
  String get chatUiKilled => 'Detenido';

  @override
  String get chatUiTruncated => 'Recortado';

  @override
  String get chatUiUpdated => 'Actualizado';

  @override
  String get chatUiError => 'Error';

  @override
  String get chatUiAssistant => 'Asistente';

  @override
  String get chatUiUser => 'Usuario';

  @override
  String get chatUiOpenCodeSession => 'Conversación de OpenCode';

  @override
  String get chatUiTool => 'Herramienta';

  @override
  String get chatUiFile => 'archivo';

  @override
  String get e7LibraryReportABug => 'Informar de un problema';

  @override
  String get e7LibraryKeyboardShortcuts => 'Atajos de teclado';

  @override
  String get e7LibraryHTTPHeader => 'Encabezado HTTP';

  @override
  String get e7LibraryEnvironmentVariable => 'variable de entorno';

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgainShortly =>
      'El servidor está reconectando. Reintenta en un momento.';

  @override
  String get e7LibraryWhatIsMCP => '¿Qué es MCP?';

  @override
  String get e7LibrarySavingConfiguration => 'Guardando la configuración';

  @override
  String get e7LibrarySaveMCPServer => 'Guardar el servidor MCP';

  @override
  String get e7LibraryThisProject => 'Este proyecto';

  @override
  String e7LibraryWritesOnlyTo(String detail1) {
    return 'Escribe solo en $detail1.';
  }

  @override
  String get e7LibraryWritesToThisOpenCodeServerSGlobal =>
      'Escribe en la configuración global de este servidor de OpenCode.';

  @override
  String get e7LibraryServerName => 'Nombre del servidor';

  @override
  String get e7LibraryDocsOrBrowserTools => 'docs o browser-tools';

  @override
  String get e7LibraryUniqueWithinTheSelectedConfiguration =>
      'Único dentro de la configuración seleccionada.';

  @override
  String get e7LibraryEnterAServerName => 'Escribe un nombre de servidor';

  @override
  String get e7LibraryRemoteURL => 'URL remota';

  @override
  String get e7LibraryLocalCommand => 'Comando local';

  @override
  String get e7LibraryOptional => 'Opcional';

  @override
  String get e7LibraryEnterAValueGreaterThanZero =>
      'Escribe un valor mayor que cero';

  @override
  String get e7LibraryMCPEndpointURL => 'URL del endpoint MCP';

  @override
  String get e7LibraryHTTPIsAcceptedForLocalDevelopmentServers =>
      'HTTP se acepta para servidores de desarrollo locales.';

  @override
  String get e7LibraryEnterAValidHTTPOrHTTPSURL =>
      'Escribe una URL HTTP o HTTPS válida sin credenciales';

  @override
  String get e7LibraryDetectOAuthAutomatically =>
      'Detectar OAuth automáticamente';

  @override
  String get e7LibraryTurnThisOffWhenTheServerUses =>
      'Desactívalo cuando el servidor use encabezados y no deba iniciar OAuth nunca.';

  @override
  String get e7LibraryCommandAndArguments => 'Comando y argumentos';

  @override
  String get e7LibraryRunsOnTheOpenCodeServerNotThis =>
      'Corre en el servidor de OpenCode, no en este teléfono. Escribe un argumento por línea.';

  @override
  String get e7LibraryEnterACommand => 'Escribe un comando';

  @override
  String get e7LibraryWorkingDirectory => 'Directorio de trabajo';

  @override
  String get e7LibraryOptionalServerPath => 'Ruta opcional del servidor';

  @override
  String get e7LibraryEnvironmentVariables => 'Variables de entorno';

  @override
  String e7LibraryInvalidOnLineUseKEYVALUE(String detail1, String detail2) {
    return '$detail1 inválido en la línea $detail2. Usa CLAVE=VALOR.';
  }

  @override
  String e7LibraryInvalidNameOnLine(String detail1, String detail2) {
    return 'Nombre de $detail1 inválido en la línea $detail2.';
  }

  @override
  String e7LibraryDuplicateName(String detail1, String detail2) {
    return 'Nombre de $detail1 duplicado: “$detail2”.';
  }

  @override
  String get e7LibraryOpenCodeIsReconnectingTryAgain =>
      'El servidor está reconectando. Reintenta.';

  @override
  String get e7LibraryRevokeAlwaysAllowedAction => '¿Revocar el acceso?';

  @override
  String get e7LibraryAction => 'Acción';

  @override
  String get e7LibraryResource => 'Recurso';

  @override
  String get e7LibraryRevokeAccess => 'Revocar el acceso';

  @override
  String get e7LibraryAlwaysAllowedActionRevoked =>
      'Acción siempre permitida revocada';

  @override
  String get e7LibraryAlwaysAllowedActions => 'Acciones siempre permitidas';

  @override
  String get e7LibraryNoAlwaysAllowedActions =>
      'No hay acciones siempre permitidas';

  @override
  String get e7LibraryTheLastActionFailed => 'La última acción falló';

  @override
  String e7LibraryRevokeAccess2(String detail1) {
    return 'Revocar el acceso de $detail1';
  }

  @override
  String get e7LibraryToolsAndCapabilities => 'Herramientas y capacidades';

  @override
  String get e7LibraryRefreshTools => 'Actualizar herramientas';

  @override
  String get e7LibraryOpenCodeToolsDependOnTheProviderAnd =>
      'Las herramientas de OpenCode dependen del proveedor y del modelo de la conversación activa.';

  @override
  String get e7LibraryChooseModel => 'Elegir modelo';

  @override
  String e7LibrarySearchTools2(String detail1) {
    return 'Buscar herramientas de $detail1';
  }

  @override
  String get e7LibraryRegisteredInventoryUnavailable =>
      'inventario registrado no disponible';

  @override
  String get e7LibraryServerCapabilityUnavailable =>
      'capacidad del servidor no disponible';

  @override
  String get e7LibraryNoToolsForThisModel =>
      'No hay herramientas para este modelo';

  @override
  String get e7LibraryNoDescriptionReturnedByOpenCode =>
      'OpenCode no devolvió ninguna descripción';

  @override
  String get e7LibraryCopyParameterSchema => 'Copiar el esquema de parámetros';

  @override
  String get e7LibraryNoProjectSelected => 'Sin proyecto seleccionado';

  @override
  String get e7LibraryNoProjectFolderIsOpenChooseOne =>
      'No hay ningún proyecto abierto. Elige uno arriba en Archivos.';

  @override
  String get e7LibrarySwitchProject => 'Cambiar de proyecto';

  @override
  String get e7LibraryWorktrees => 'Árboles de trabajo';

  @override
  String get e7LibraryManagedWorkspaces => 'Entornos en la nube';

  @override
  String get e7LibraryProjectHealth => 'Salud del proyecto';

  @override
  String get e7LibraryOpenCodeIsReconnecting => 'OpenCode está reconectando.';

  @override
  String get e7LibraryCloudEnvironments => 'Entornos en la nube';

  @override
  String get e7LibraryDiscoverExistingEnvironments =>
      'Descubrir entornos existentes';

  @override
  String get e7LibraryNewEnvironment => 'Entorno nuevo';

  @override
  String get e7LibraryEnvironments => 'Entornos';

  @override
  String get e7LibraryNoCloudEnvironments => 'No hay entornos en la nube';

  @override
  String get e7LibraryEnvironmentRefreshFailed =>
      'Falló la actualización de entornos';

  @override
  String get e7LibraryRetryCloudEnvironments => 'Reintentar';

  @override
  String get e7LibraryAdapterRefreshFailed =>
      'Falló la actualización del adaptador';

  @override
  String get e7LibraryConnected => 'Conectado';

  @override
  String get e7LibraryConnecting => 'Conectando';

  @override
  String get e7LibraryDisconnected => 'Desconectado';

  @override
  String get e7LibraryEnvironmentActions => 'Acciones del entorno';

  @override
  String get e7LibraryOpenAgain => 'Abrir otra vez';

  @override
  String get e7LibraryNewManagedWorkspace => 'Entorno en la nube nuevo';

  @override
  String get e7LibraryCreateAndOpen => 'Crear y abrir';

  @override
  String e7LibraryRemove(String detail1) {
    return '¿Eliminar $detail1?';
  }

  @override
  String get e7LibraryRemovePermanently => 'Eliminar permanentemente';

  @override
  String e7LibraryIsReady(String detail1) {
    return '$detail1 está listo';
  }

  @override
  String get e7LibraryOpenCodeCouldNotPrepareThisWorktree =>
      'No se pudo preparar este árbol de trabajo.';

  @override
  String e7LibraryWasCreatedItsSetupStatusIsNot(String detail1) {
    return '$detail1 se creó. Su estado de configuración aún no está confirmado.';
  }

  @override
  String get e7LibraryWaitForOpenCodeToFinishPreparingThis =>
      'Espera a que este trabajo de preparación termine.';

  @override
  String get e7LibraryOpenCodeDidNotSwitchLocations =>
      'El servidor no cambió de proyecto.';

  @override
  String e7LibraryCouldNotVerifyBeforeThisDestructiveAction(
    String detail1,
    String detail2,
  ) {
    return 'No se pudo verificar $detail1 antes de esta acción destructiva: $detail2';
  }

  @override
  String e7LibraryResetToTheDefaultBranch(String detail1) {
    return '$detail1 se reajustó a la rama por defecto';
  }

  @override
  String e7LibraryAndItsBranchWereRemoved(String detail1) {
    return '$detail1 y su rama se eliminaron';
  }

  @override
  String e7LibraryReset(String detail1) {
    return '¿Reajustar $detail1?';
  }

  @override
  String get e7LibraryThisPermanentlyDiscardsTrackedChangesAndDeletes =>
      'Esto descarta permanentemente los cambios registrados y elimina todos los archivos no registrados e ignorados. Los submódulos también se reajustan y se limpian. Esto no se puede deshacer.';

  @override
  String get e7LibraryResetWorktree => 'Reajustar el árbol de trabajo';

  @override
  String get e7LibraryRefreshWorktrees => 'Actualizar los árboles de trabajo';

  @override
  String get e7LibraryNewWorktree => 'Árbol de trabajo nuevo';

  @override
  String get e7LibraryNoIsolatedWorktreesYet =>
      'Aún no hay árboles de trabajo aparte';

  @override
  String get e7LibraryPreparingFilesAndProjectTasks =>
      'Preparando archivos y tareas del proyecto…';

  @override
  String get e7LibraryWorktreeActions => 'Acciones del árbol de trabajo';

  @override
  String get e7LibraryReset2 => 'Reajustar';

  @override
  String get e7LibraryNameOptional => 'Nombre (opcional)';

  @override
  String get e7LibraryTheWorktreeDirectoryAndItsGitBranch =>
      'La carpeta del árbol de trabajo y su rama de Git se eliminarán permanentemente. Las conversaciones existentes quedan en el historial, pero su directorio de trabajo dejará de existir.';

  @override
  String get e7LibraryInitializeGitRepository =>
      '¿Inicializar el repositorio de Git?';

  @override
  String get e7LibraryOpenCodeWillRunGitInitInThe =>
      'OpenCode ejecutará git init en el proyecto actual. Los archivos existentes no se cambiarán ni se comprometerán. Esto activa las funciones de rama, árbol de trabajo y Revisión.';

  @override
  String get e7LibraryInitializeGit => 'Inicializar Git';

  @override
  String get e7LibraryGitRepositoryInitialized =>
      'Repositorio de Git inicializado';

  @override
  String get e7LibraryVersionControl => 'Control de versiones';

  @override
  String get e7LibraryLanguageServices => 'Servicios de lenguaje';

  @override
  String get e7LibraryFormatters => 'Formateadores';

  @override
  String get e7LibraryGitIsNotInitialized => 'Git no está inicializado';

  @override
  String get e7LibraryInitializeThisProjectToEnableBranchesWorking =>
      'Inicializa este proyecto para activar ramas, cambios de árbol de trabajo y Revisión.';

  @override
  String get e7LibraryRunGitInitFromATerminal =>
      'Ejecuta `git init` desde una terminal';

  @override
  String get e7LibraryGitInitializationFailed =>
      'Falló la inicialización de Git';

  @override
  String get e7LibraryNoActiveBranch => 'No hay rama activa';

  @override
  String e7LibraryDefaultBranch(String detail1) {
    return 'Rama por defecto: $detail1';
  }

  @override
  String get e7LibraryWorkingTreeIsClean => 'El árbol de trabajo está limpio';

  @override
  String e7LibraryChangedFiles(String detail1) {
    return '$detail1 archivos cambiados';
  }

  @override
  String get e7LibraryNoUncommittedChanges => 'No hay cambios sin comprometer';

  @override
  String get e7LibraryNoActiveLanguageServices =>
      'No hay servicios de lenguaje activos';

  @override
  String get e7LibraryOpenCodeActivatesThemWhileItInspectsSupported =>
      'OpenCode los activa mientras inspecciona archivos de fuentes admitidos al programar.';

  @override
  String get e7LibraryNoFormattersConfigured =>
      'No hay formateadores configurados';

  @override
  String get e7LibraryEnabled => 'Activado';

  @override
  String get e7LibraryDisabled => 'Desactivado';

  @override
  String e7LibraryLoading(String detail1) {
    return 'Cargando $detail1';
  }

  @override
  String get e7LibraryAutomaticCallbackCaptureIsUnavailablePasteThe =>
      'La captura automática del callback no está disponible. Pega la URL de callback o el código de autorización.';

  @override
  String get e7LibraryThePhoneIsSecurelyListeningForThis =>
      'El teléfono escucha de forma segura el callback de esta autorización. También puedes escribirlo a mano.';

  @override
  String get e7LibraryCallbackURLOrCode => 'URL de callback o código';

  @override
  String get e7LibraryUpdating => 'Actualizando…';

  @override
  String get e7LibraryNotConnected => 'No conectado';

  @override
  String get e7LibraryServerEnvironment => 'Entorno del servidor';

  @override
  String get e7LibraryServerManaged => 'Gestionado por el servidor';

  @override
  String get e7LibraryConnect => 'Conectar';

  @override
  String get e7LibraryAuthenticationFailed => 'Falló la autenticación';

  @override
  String get e7LibraryAuthenticationAttemptExpired =>
      'El intento de autenticación expiró';

  @override
  String get e7LibraryAuthenticationComplete => 'Autenticación completada';

  @override
  String get e7LibraryReturnFromTheBrowserAndEnterThe =>
      'Vuelve del navegador e ingresa el código de autorización.';

  @override
  String get e7LibraryFinishAuthenticationInTheBrowserThenCheck =>
      'Completa la autenticación en el navegador y luego revisa su estado.';

  @override
  String get e7LibraryAuthorizationCode => 'Código de autorización';

  @override
  String get e7LibrarySelectAnOption => 'Elige una opción';

  @override
  String get e7LibraryEnterAValue => 'Escribe un valor';

  @override
  String get e7LibraryTheServerReturnedAnUnsafeAuthorizationLink =>
      'El servidor devolvió un enlace de autorización inseguro. Solo se permiten enlaces HTTPS con un host válido y sin credenciales incrustadas.';

  @override
  String get e7LibraryCouldNotLoadThisSection =>
      'No se pudo cargar esta sección';

  @override
  String get e7LibrarySkills => 'Skills';

  @override
  String get e7LibraryNoSkillsAvailable => 'No hay skills disponibles';

  @override
  String get e7LibraryModelsAndAgents => 'Modelos y agentes';

  @override
  String get e7LibraryUnavailable => 'No disponible';

  @override
  String get e7LibraryFinishOrCancelTheCurrentMCPAuthorization =>
      'Termina o cancela la autorización MCP actual primero.';

  @override
  String get e7LibraryCouldNotOpenTheAuthorizationPage =>
      'No se pudo abrir la página de autorización';

  @override
  String e7LibraryAuthenticated(String detail1) {
    return '$detail1 autenticado';
  }

  @override
  String get e7LibraryCouldNotConfirmMCPAuthentication =>
      'No se pudo confirmar la autenticación MCP';

  @override
  String get e7LibraryMCPServerSavedInOpenCode =>
      'Servidor MCP guardado en OpenCode';

  @override
  String get e7LibraryMCPUnavailable => 'MCP no disponible';

  @override
  String get e7LibraryMCPAndIntegrations => 'Proveedores y MCP';

  @override
  String get e7LibraryCouldNotSaveSignInRecovery =>
      'No se pudo guardar la recuperación del inicio de sesión.';

  @override
  String get e7LibraryNoProviderConnectionsAvailable =>
      'No hay conexiones de proveedor disponibles';

  @override
  String get e7LibraryThisServerDidNotReturnAnyProvider =>
      'Este servidor no devolvió integraciones de proveedor.';

  @override
  String get e7LibrarySearchProvidersOrModels => 'Buscar proveedores o modelos';

  @override
  String get e7LibraryNoMCPServersConfigured =>
      'No hay servidores MCP configurados';

  @override
  String get e7LibrarySaveOneForThisProjectOrEvery =>
      'Guarda uno para este proyecto o para todos los proyectos del servidor.';

  @override
  String get e7LibraryAddAnMCPServer => 'Agregar un servidor MCP';

  @override
  String get e7LibraryAuthorizing => 'Autorizando';

  @override
  String get e7LibraryResources => 'Recursos';

  @override
  String get e7LibraryNoResourcesAvailable => 'No hay recursos disponibles';

  @override
  String get e7LibraryConnectedMCPServersHaveNotExposedAny =>
      'Los servidores MCP conectados no expusieron ningún recurso.';

  @override
  String get e7LibraryOpenBrowser => 'Abrir el navegador';

  @override
  String get e7LibraryConnectedAndToolsAreAvailable =>
      'Conectado, y las herramientas están disponibles';

  @override
  String get e7LibraryConnectionFailed => 'Falló la conexión';

  @override
  String get e7LibraryAuthenticationRequired => 'Autenticación requerida';

  @override
  String get e7LibraryClientRegistrationRequired =>
      'Se requiere registro del cliente';

  @override
  String e7LibraryStoredCredential(String detail1) {
    return 'Credencial guardada: $detail1';
  }

  @override
  String get e7LibraryNoConnectionMethodsAvailable =>
      'No hay métodos de conexión disponibles';

  @override
  String get e7LibraryConfiguredOnTheServer => 'Configurado en el servidor';

  @override
  String e7LibraryDisconnect2(String detail1) {
    return '¿Desconectar $detail1?';
  }

  @override
  String e7LibraryCredentialRemovedServerEnvironmentRemainsActive(
    String detail1,
  ) {
    return 'Credencial de $detail1 eliminada; el entorno del servidor sigue activo';
  }

  @override
  String e7LibraryDisconnected2(String detail1) {
    return '$detail1 desconectado';
  }

  @override
  String e7LibraryConnect2(String detail1) {
    return 'Conectar $detail1';
  }

  @override
  String get e7LibraryAuthorizationWasNotOpenedThePendingAttempt =>
      'La autorización no se abrió. El intento pendiente se conserva.';

  @override
  String get e7LibraryCouldNotOpenOAuth => 'No se pudo abrir OAuth';

  @override
  String e7LibraryIsConnected(String detail1) {
    return '$detail1 está conectado';
  }

  @override
  String get e7LibraryTheSignInSourceChanged =>
      'La fuente del inicio de sesión cambió.';

  @override
  String get e7LibraryCouldNotConfirmAuthenticationReturnToThe =>
      'No se pudo confirmar la autenticación. Vuelve a la fuente original y reintenta.';

  @override
  String get e7LibraryNoServerCommandsFound =>
      'No se encontraron comandos de servidor';

  @override
  String get e7LibraryCommandsFromYourProjectAndSkillsAppear =>
      'Los comandos de tu proyecto y de tus skills aparecen aquí.';

  @override
  String get e7LibraryNoDescription => 'Sin descripción';

  @override
  String get e7LibraryServerCommands => 'Comandos del servidor';

  @override
  String get e7LibraryReferences => 'Referencias';

  @override
  String get e7LibraryNoReferencesConfigured =>
      'No hay referencias configuradas';

  @override
  String e7LibraryChangedFilesDetected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se detectaron $count archivos cambiados',
      one: 'Se detectó 1 archivo cambiado',
    );
    return '$_temp0';
  }

  @override
  String get e7LibraryEnvironmentRemainsAfterDisconnect =>
      'Este proveedor también usa el entorno del servidor, que lo móvil no puede eliminar y que seguirá activo.';

  @override
  String get e7LibrarySearchImportAliases =>
      'restaurar copia de seguridad transferir JSON conversación sesión chat';

  @override
  String get e7LibrarySearchShortcutsAliases => 'atajos ayuda escritorio';

  @override
  String get e7SetupApiKeyHint => 'Pega una clave de API';

  @override
  String get e7SetupBrowserHint =>
      'Abre un navegador. Si la redirección no llega a OpenCode, pega aquí la URL de retorno.';

  @override
  String get e7SetupDeviceCodeHint =>
      'Usa un código de un solo uso. Funciona desde un teléfono.';

  @override
  String get e7SetupAccountHint => 'Inicia sesión con tu cuenta';

  @override
  String get e7SetupNewTerminalDetail =>
      'Inicia un shell en el proyecto activo.';

  @override
  String get e7SetupShowPassword => 'Mostrar la contraseña del servidor';

  @override
  String get e7SetupAuthFailed =>
      'El servidor se inició pero falló la autenticación.';

  @override
  String get e7SetupScanInstruction =>
      'Apunta la cámara al código QR que imprime opencode2 pair.';

  @override
  String get e7SetupRightKey => 'Tecla flecha derecha';

  @override
  String get e7SetupRestartReconnectFailed =>
      'El servidor local se reinició, pero la app no pudo reconectarse.';

  @override
  String get e7SetupInstallingOpenCode => 'Instalando OpenCode';

  @override
  String get e7SetupNoOutput => 'Aún no hay salida del terminal.';

  @override
  String get e7SetupFollowLog => 'Seguir el registro del servidor';

  @override
  String get e7SetupOpenSetupGuide => 'Abrir la guía de configuración';

  @override
  String get e7SetupDownKey => 'Tecla flecha abajo';

  @override
  String get e7SetupVerifyContinue => 'Verificar y continuar';

  @override
  String get e7SetupAccessibleTerminal =>
      'Usar transcripción y entrada accesibles';

  @override
  String get e7SetupCameraFailedDetail =>
      'Puede que otra app esté usando la cámara. Pegar el código de emparejamiento funciona igual.';

  @override
  String get e7SetupStopTerminalDetail =>
      'Se terminará el proceso en ejecución y sus procesos hijo.';

  @override
  String get e7SetupEdit => 'Editar';

  @override
  String get e7SetupServerPassword => 'Contraseña del servidor';

  @override
  String get e7SetupHttpsHint =>
      'Prefiere HTTPS. HTTP remoto necesita tu permiso para cada servidor.';

  @override
  String get e7SetupObservedVersionSaveFailed =>
      'El servidor local está listo, pero no se pudo guardar la versión observada. Actualiza la configuración para intentarlo de nuevo.';

  @override
  String get e7SetupPasteInstead => 'Pegar en su lugar';

  @override
  String get e7SetupConfirmUpdate => '¿Actualizar el OpenCode administrado?';

  @override
  String get e7SetupInstallServiceDetail =>
      'Instalador oficial más un servicio de usuario de systemd que sobrevive a terminales cerrados y reinicios.';

  @override
  String get e7SetupUpdateHost => 'Actualizar OpenCode en el host';

  @override
  String get e7SetupKeepAfterLogout =>
      'Mantenerlo en marcha tras cerrar sesión';

  @override
  String get e7SetupRestartServer => 'Reiniciar el servidor';

  @override
  String get e7SetupResumeSetup =>
      'Reintentar — retoma donde se quedó la configuración';

  @override
  String get e7SetupHidePassword => 'Ocultar la contraseña del servidor';

  @override
  String get e7SetupControlKeys =>
      'Teclas de control del terminal. Desliza horizontalmente para ver más.';

  @override
  String get e7SetupEndInputKey => 'Fin de entrada, Control D';

  @override
  String get e7SetupGuidanceSaveFailed =>
      'No se pudo guardar la guía de conexión. Vuelve a intentar guardarla.';

  @override
  String get e7SetupServerOperation => 'Operación del servidor en curso';

  @override
  String get e7SetupNewTerminal => 'Terminal nuevo';

  @override
  String get e7SetupUnsavedProfile => 'El servidor no se ha guardado.';

  @override
  String get e7SetupCheckingInstall => 'Comprobando el entorno instalado...';

  @override
  String get e7SetupUsername => 'Usuario (opcional)';

  @override
  String get e7SetupFirstSetupDuration =>
      'La primera configuración puede tardar de 10 a 15 minutos. Puedes salir de esta pantalla y volver; la configuración sigue en marcha.';

  @override
  String get e7SetupNoTerminals => 'No hay procesos de terminal';

  @override
  String get e7SetupEscapeKey => 'Tecla Escape';

  @override
  String get e7SetupLeftKey => 'Tecla flecha izquierda';

  @override
  String get e7SetupInstallService =>
      'Instalar OpenCode como servicio en segundo plano';

  @override
  String get e7SetupSaveToFinish => 'Conectado: guarda para terminar.';

  @override
  String get e7SetupPasswordStartupHint =>
      'Se muestra al iniciar el servidor. Déjala vacía si no tiene.';

  @override
  String get e7SetupStopBeforeUpdate =>
      'Detén la generación activa antes de actualizar OpenCode.';

  @override
  String get e7SetupRestartingLocal => 'Reiniciando el servidor local';

  @override
  String get e7SetupAddServer => 'Agregar servidor';

  @override
  String get e7SetupInteractiveTerminal => 'Usar terminal interactivo';

  @override
  String get e7SetupInstallingUbuntu => 'Configurando Ubuntu';

  @override
  String get e7SetupInterruptKey => 'Interrumpir, Control C';

  @override
  String get e7SetupServerUrl => 'URL del servidor';

  @override
  String get e7SetupUsbAccess => 'Accede desde este teléfono por USB';

  @override
  String get e7SetupDiscardChanges => '¿Descartar los cambios del servidor?';

  @override
  String get e7SetupPasswordRequired =>
      'No se puede leer la contraseña guardada';

  @override
  String get e7SetupPairing => 'Emparejando…';

  @override
  String get e7SetupCommandInput => 'Entrada de comandos del terminal';

  @override
  String get e7SetupCameraFailed => 'No se pudo abrir la cámara';

  @override
  String get e7SetupPastePassword => 'Pegar contraseña del servidor';

  @override
  String get e7SetupSavingLocal => 'Guardando los ajustes del servidor local';

  @override
  String get e7SetupCopyFailureReport => 'Copiar informe del fallo';

  @override
  String get e7SetupCameraDisabled => 'El acceso a la cámara está desactivado';

  @override
  String get e7SetupPastePairing => 'Pegar código de emparejamiento';

  @override
  String get e7SetupServers => 'Servidores';

  @override
  String get e7SetupTransportReconnecting =>
      'El transporte del servidor se está reconectando.';

  @override
  String get e7SetupStepUnavailable => 'aún no disponible';

  @override
  String get e7SetupUpdateHostDetail =>
      'Cuando el servidor informa de una actualización, Ajustes ofrece primero la actualización nativa; esto es el equivalente en el host.';

  @override
  String get e7SetupMissingPasswordShort =>
      'La contraseña guardada no está disponible. Introdúcela de nuevo, o déjala vacía solo si este servidor ya no requiere una.';

  @override
  String get e7SetupTesting => 'Probando…';

  @override
  String get e7SetupScanPairing => 'Escanear código de emparejamiento';

  @override
  String get e7SetupRestartUnconfirmed =>
      'No se pudo confirmar este reinicio. Actualiza su progreso antes de reintentar.';

  @override
  String get e7SetupExistingMissingCredential =>
      'Existe un servidor local, pero su credencial guardada no está disponible. Ejecuta la configuración de nuevo para reemplazarla de forma segura.';

  @override
  String get e7SetupPreparingModels => 'Preparando los modelos';

  @override
  String get e7SetupHostInstructions =>
      'Estos comandos se ejecutan en la computadora que aloja este servidor; la app no puede ejecutarlos por ti. Copia cada uno en un terminal de esa máquina.';

  @override
  String get e7SetupTranscript => 'Transcripción del terminal';

  @override
  String get e7SetupHostFirstSetup =>
      'Primera configuración: ejecútala en tu computadora';

  @override
  String get e7SetupNoCameraDetail =>
      'No hay nada con qué escanear. Ejecuta opencode2 pair en el servidor, copia el código que imprime y pégalo en el editor del servidor.';

  @override
  String get e7SetupDiscard => 'Descartar';

  @override
  String get e7SetupConnectionClosed => 'Conexión cerrada';

  @override
  String get e7SetupUpKey => 'Tecla flecha arriba';

  @override
  String get e7SetupConnect => 'Conectar';

  @override
  String get e7SetupRemoveTerminalDetail =>
      'Se quitará el registro de este terminal.';

  @override
  String get e7SetupInputDisconnected =>
      'La entrada no está disponible sin conexión.';

  @override
  String get e7SetupHostCopied =>
      'Copiado. Ejecútalo en la computadora del servidor.';

  @override
  String get e7SetupCameraPrivacy =>
      'La cámara se usa solo para leer el QR que imprime opencode2 pair, y solo mientras esta pantalla está abierta. Puedes pegar el código en su lugar: hace exactamente lo mismo.';

  @override
  String get e7SetupIsV2 => 'Este es un servidor OpenCode 2.';

  @override
  String get e7SetupResumeLive => 'Reanudar la vista en vivo';

  @override
  String get e7SetupTabKey => 'Tecla Tab';

  @override
  String get e7SetupCloseScanner => 'Cerrar el escáner';

  @override
  String get e7SetupChooseContinue => 'Elige cómo continuar';

  @override
  String get e7SetupCameraNeeded =>
      'Se necesita acceso a la cámara para escanear';

  @override
  String get e7SetupTerminalActions => 'Acciones del terminal';

  @override
  String get e7SetupReadPassword =>
      'Leer la contraseña del servidor para esta app';

  @override
  String get e7SetupStartInstalled => '¿Iniciar el OpenCode instalado?';

  @override
  String get e7SetupTestConnection => 'Probar conexión';

  @override
  String get e7SetupDefaultServer => 'Servidor OpenCode';

  @override
  String get e7SetupSaving => 'Guardando…';

  @override
  String get e7SetupLinuxService => 'Configurar OpenCode 2';

  @override
  String get e7SetupPairingDesktopHint =>
      'Comprueba que el servidor esté en ejecución y que la dirección que imprimió sea accesible desde esta máquina.';

  @override
  String get e7SetupAboutNotices => 'Acerca de y avisos de código abierto';

  @override
  String get e7SetupReportCopied => 'Informe del fallo copiado.';

  @override
  String get e7SetupNoCamera => 'Este dispositivo no tiene cámara';

  @override
  String get e7SetupServerDisconnected => 'El servidor no está conectado.';

  @override
  String get e7SetupEmptyPasswordHint =>
      'Déjala vacía solo si este servidor ya no usa contraseña.';

  @override
  String get e7SetupAndroidOnly =>
      'La configuración en este teléfono solo está disponible en Android';

  @override
  String get e7SetupEditServer => 'Editar servidor';

  @override
  String get e7SetupReenterPassword => 'Vuelve a ingresar la contraseña';

  @override
  String get e7SetupMissingCredential =>
      'La credencial guardada de este servidor administrado no está disponible. Ejecuta la configuración de nuevo para reemplazarla de forma segura.';

  @override
  String get e7SetupReconnect => 'Reconectar';

  @override
  String get e7SetupSwitchNotStarted =>
      'El cambio de entorno de ejecución no se inició.';

  @override
  String get e7SetupNoUbuntu =>
      'No se encontró ninguna instalación de Ubuntu administrada.';

  @override
  String get e7SetupKeyUnavailable =>
      'No disponible mientras la terminal está desconectada';

  @override
  String get e7SetupCopyTerminal =>
      'Copiar la selección o la transcripción del terminal';

  @override
  String get e7SetupCommandHint => 'Escribe un comando';

  @override
  String get e7SetupCheckInstallFailed =>
      'No se pudo comprobar el entorno instalado.';

  @override
  String get e7SetupConnectionFailed => 'Falló la conexión.';

  @override
  String get e7SetupOpenAppSettings => 'Abrir ajustes de la app';

  @override
  String get e7SetupFullWalkthrough =>
      'Guía completa (se abre en el navegador)';

  @override
  String get e7SetupPairingPhoneHint =>
      'Un servidor vinculado a su propio 127.0.0.1 no es accesible desde este teléfono hasta que lo conectes con un puente: “adb reverse tcp:PORT tcp:PORT” por USB, o un reenvío SSH. Para acceder por la red, colócalo detrás de HTTPS.';

  @override
  String get e7SetupOutputCopied => 'Salida de la configuración copiada.';

  @override
  String get e7SetupMissingPasswordLong =>
      'La contraseña guardada no está disponible. Introdúcela de nuevo, o déjala vacía solo si este servidor ya no requiere contraseña.';

  @override
  String get e7SetupNoServerGuide =>
      '¿Aún no hay un servidor? La guía de configuración explica cómo iniciar uno.';

  @override
  String get e7SetupStartingLocal => 'Iniciando el servidor local';

  @override
  String get e7SetupTerminalSemantics =>
      'Terminal interactivo. Usa el botón de accesibilidad para ver una transcripción legible y la entrada con etiqueta.';

  @override
  String get e7SetupRestartingLocalStage => 'Reiniciando el servidor local';

  @override
  String get e7SetupEmptyPairClipboard =>
      'El portapapeles está vacío. Ejecuta “opencode2 pair” en el servidor y copia el código que imprime.';

  @override
  String get e7SetupRestartActiveChanged =>
      'El servidor local se reinició, pero el servidor activo cambió. Reconéctate cuando quieras.';

  @override
  String get e7SetupTokenRequired => 'No se puede leer el token guardado';

  @override
  String get e7SetupPairingInstructions =>
      'En tu computadora ejecuta «opencode2 pair» y luego pega o escanea el código que imprime.';

  @override
  String get e7SetupHostDaily => 'Uso diario — se ejecuta en tu computadora';

  @override
  String get e7SetupStopLocal => 'Detener servidor local';

  @override
  String get e7SetupReadingProgress =>
      'Leyendo el progreso de la configuración';

  @override
  String get e7SetupCameraSettingsDetail =>
      'Android no volverá a preguntar, así que debes cambiarlo en los ajustes de la app: activa Cámara y vuelve. Pegar el código no necesita ningún permiso y funciona ahora mismo.';

  @override
  String get e7SetupUbuntuOnly => 'Ubuntu está instalado. OpenCode aún no.';

  @override
  String get e7SetupRenameTerminal => 'Renombrar terminal';

  @override
  String get e7SetupInputUnavailable => 'Entrada del terminal no disponible';

  @override
  String get e7SetupUpdateInterruption =>
      'El servidor no estará disponible por un momento. Conviene detener antes la generación activa.';

  @override
  String get e7SetupRunningOnPhone =>
      'OpenCode se está ejecutando en este teléfono.';

  @override
  String get e7SetupLocalStopped =>
      'El servidor local está detenido. Sus archivos instalados se conservan.';

  @override
  String get e7SetupDidNotConnect => 'El servidor no se conectó.';

  @override
  String get e7SetupSendCommand => 'Enviar comando al terminal';

  @override
  String get e7SetupSendKey => 'Envía esta tecla al terminal';

  @override
  String get e7SetupRemoveTerminal => '¿Quitar el terminal?';

  @override
  String e7SetupTerminalNumber(int number) {
    return 'Terminal $number';
  }

  @override
  String e7SetupCopyCommandLabel(String label) {
    return 'Copiar comando: $label';
  }

  @override
  String e7SetupConnectFailedDetail(String name, String detail) {
    return 'No se pudo conectar con $name. $detail Revisa la dirección y las credenciales del servidor e inténtalo de nuevo.';
  }

  @override
  String e7SetupSavedConnectFailed(String name, String detail) {
    return '$name se guardó, pero no pudo conectarse. Revisa la dirección y las credenciales del servidor e inténtalo de nuevo. ($detail)';
  }

  @override
  String e7SetupSaveFailed(String name, String detail) {
    return 'No se pudo guardar $name. El servidor existente quedó sin cambios. Revisa el almacenamiento del dispositivo e inténtalo de nuevo. ($detail)';
  }

  @override
  String e7SetupRemoveServer(String name) {
    return '¿Quitar $name?';
  }

  @override
  String e7SetupRemovedDisconnectFailed(String name, String detail) {
    return 'Se quitó $name, pero su conexión no se pudo cerrar correctamente. Reinicia la app antes de conectarte a otro lugar. ($detail)';
  }

  @override
  String e7SetupRemoveFailed(String name, String detail) {
    return 'No se pudo quitar $name. Se conservaron el servidor guardado y la conexión actual. Revisa el almacenamiento del dispositivo e inténtalo de nuevo. ($detail)';
  }

  @override
  String e7SetupPairedChoice(String host, int count) {
    return 'Emparejado con $host, elegido entre $count direcciones del código.';
  }

  @override
  String e7SetupPaired(String host) {
    return 'Emparejado con $host.';
  }

  @override
  String e7SetupDeleteDisclosure(int queued, int drafts) {
    String _temp0 = intl.Intl.pluralLogic(
      queued,
      locale: localeName,
      other: 'Se eliminarán $queued mensajes en la fila.',
      one: 'Se eliminará 1 mensaje en la fila.',
      zero: '',
    );
    String _temp1 = intl.Intl.pluralLogic(
      drafts,
      locale: localeName,
      other: 'Se eliminarán $drafts borradores sin enviar.',
      one: 'Se eliminará 1 borrador sin enviar.',
      zero: '',
    );
    return 'Esto borra todo lo que este dispositivo almacenó para el servidor: su contraseña, el modelo y el agente seleccionados, la elección de proyecto y las conversaciones que se muestran en el widget de la pantalla de inicio.\n\n$_temp0 $_temp1\n\nNada se borra del servidor mismo ni de tus proveedores de IA.';
  }

  @override
  String e7SetupPairingFailed(String detail, String hint) {
    return 'Ninguna dirección del código de emparejamiento respondió:\n$detail\n$hint';
  }

  @override
  String e7SetupProbeV2(String version) {
    return 'OpenCode 2 · $version';
  }

  @override
  String get e7SetupPairNone =>
      'Aquí no hay ningún código de vinculación. Ejecuta «opencode2 pair» en el servidor y escanea o copia lo que imprime.';

  @override
  String get e7SetupPairLong =>
      'Eso es demasiado largo para ser un código de vinculación. Copia solo la línea que imprime «opencode2 pair» o escanea su código QR.';

  @override
  String get e7SetupPairInvalid =>
      'Eso no es un código de vinculación. Ejecuta «opencode2 pair» en el servidor y escanea o copia lo que imprime.';

  @override
  String get e7SetupPairShape =>
      'Ese código de vinculación tiene un formato incorrecto: debe ser un objeto JSON con «urls», «username» y «password».';

  @override
  String get e7SetupPairNoUrls =>
      'Ese código de vinculación no tiene el campo «urls», así que no hay dirección a la que conectar.';

  @override
  String get e7SetupPairUrlsType =>
      'El campo «urls» de ese código de vinculación no es una lista de direcciones.';

  @override
  String get e7SetupPairTooMany =>
      'Ese código de vinculación incluye más direcciones de las que la app probará. Asocia el servidor a una sola interfaz y vincula de nuevo.';

  @override
  String get e7SetupPairAddressType =>
      'Ese código de vinculación incluye una dirección que no es texto.';

  @override
  String get e7SetupPairAddressLong =>
      'Ese código de vinculación incluye una dirección demasiado larga para ser una URL de servidor.';

  @override
  String get e7SetupPairAddressMissing =>
      'Ese código de vinculación no trae ninguna dirección de servidor. Comprueba que el servidor esté escuchando y ejecuta «opencode2 pair» de nuevo.';

  @override
  String get e7SetupPairUsernameType =>
      'El campo «username» de ese código de vinculación no es texto.';

  @override
  String get e7SetupPairPasswordMissing =>
      'Ese código de vinculación no tiene el campo «password». Puede que esté cortado: escanea o copia el código completo.';

  @override
  String get e7SetupPairPasswordType =>
      'El campo «password» de ese código de vinculación no es texto.';

  @override
  String get e7SetupPairTestFailed =>
      'La prueba de conexión falló antes de poder comprobar el servidor. Prueba con otra dirección.';

  @override
  String get e7SetupNotOpenCode =>
      'La dirección no respondió como un servidor OpenCode. Revisa la dirección e inténtalo de nuevo.';

  @override
  String get e7SetupNoServerAnswer =>
      'El servidor no respondió. Comprueba que esté en marcha y que la dirección sea correcta.';

  @override
  String get e7SetupPairPasswordRejected =>
      'Contraseña rechazada. Revisa el código de vinculación e inténtalo de nuevo.';

  @override
  String get e7SetupPairAddressUnusable =>
      'Ese código de vinculación contiene una dirección de servidor inutilizable.';

  @override
  String get e7SetupInvalidAddress => '<dirección no válida>';

  @override
  String get e7SetupEnterUrl => 'Escribe una URL de servidor.';

  @override
  String get e7SetupIncludeScheme =>
      'Incluye https:// o http://. HTTP remoto necesita tu permiso.';

  @override
  String get e7SetupCompleteUrl =>
      'Escribe una URL completa del servidor, como https://server.example:4096.';

  @override
  String get e7SetupUrlScheme =>
      'Las URL de servidor deben usar https:// o http://.';

  @override
  String get e7SetupUrlCredentials =>
      'No incluyas credenciales en la URL. Usa los campos de abajo.';

  @override
  String get e7SetupUrlQuery =>
      'Quita los parámetros de consulta y los fragmentos de la URL del servidor.';

  @override
  String get e7SetupUrlPath =>
      'Quita la ruta de la URL del servidor. Escribe solo su origen.';

  @override
  String get e7SetupRequireHttps =>
      'Una contraseña solo se envía a otra computadora por https://. Usa la dirección https:// de la computadora, vincula con un código o conéctate con Tailscale.';

  @override
  String get e7SetupLocalHttp =>
      'Una dirección http:// solo funciona con este teléfono o con una dirección de red privada como 192.168.x.x. Para cualquier otra, vincula con un código, usa su dirección https:// o conéctate con Tailscale.';

  @override
  String get e7SetupRefused =>
      'La computadora rechazó la conexión. Comprueba que el servidor esté en marcha allí y que la dirección y el puerto sean correctos.';

  @override
  String get e7SetupTimeout =>
      'Se agotó el tiempo de la conexión. Revisa la dirección y que el servidor sea accesible desde este teléfono.';

  @override
  String get e7SetupDns =>
      'No se encontró ese nombre de host. Revisa cómo escribiste la dirección.';

  @override
  String get e7SetupCertificate =>
      'El certificado no es de confianza. Comprueba su huella antes de decidir si confiar en él.';

  @override
  String get e7SetupUnhealthy =>
      'El servidor respondió, pero informó que no está sano. Revisa sus registros e inténtalo de nuevo.';

  @override
  String get e7SetupServerStarting =>
      'El servidor se está iniciando. Inténtalo de nuevo en un momento.';

  @override
  String get e7SetupPasswordNeeded =>
      'Este servidor requiere su contraseña de serve.';

  @override
  String get e7SetupPasswordRejected =>
      'Contraseña rechazada. Copia la línea «server password» actual de la salida del servidor: cambia en cada reinicio a menos que se defina OPENCODE_PASSWORD.';

  @override
  String get e7SetupCredentialsRefused =>
      'El servidor rechazó las credenciales. Revisa el usuario y la contraseña.';

  @override
  String get e7SetupCodexUrl => 'Escribe una URL de servidor de Codex.';

  @override
  String get e7SetupCodexCompleteUrl =>
      'Escribe una URL completa del servidor de Codex.';

  @override
  String get e7SetupCodexScheme =>
      'Las URL de servidor de Codex deben usar wss://, o ws:// para un servidor local.';

  @override
  String get e7SetupCodexCredentials =>
      'No incluyas credenciales en la URL de Codex.';

  @override
  String get e7SetupCodexQuery =>
      'Quita los parámetros de consulta y los fragmentos de la URL de Codex.';

  @override
  String get e7SetupCodexPath =>
      'Quita la ruta de la URL del servidor de Codex.';

  @override
  String get e7SetupCodexPlain =>
      'ws:// solo funciona con un servidor en este teléfono. Usa una dirección wss:// para otra computadora.';

  @override
  String get e7SetupCodexDirectory =>
      'Escribe una ruta absoluta de proyecto de Codex.';

  @override
  String get e7SetupCodexToken =>
      'Escribe un token de conexión de Codex válido.';

  @override
  String get e7SetupInstallUbuntu => 'Instalando el entorno de Ubuntu';

  @override
  String get e7SetupRefreshModels =>
      'Actualizando el catálogo de modelos de OpenCode';

  @override
  String get e7SetupStartLocalServer => 'Iniciando el servidor local';

  @override
  String get e7SetupOpenCodeReady => 'OpenCode está listo';

  @override
  String get e7SetupPrepareRuntime =>
      'Preparando el runtime de OpenCode seleccionado';

  @override
  String get e7SetupSwitchLocal => 'Cambiando el servidor local administrado';

  @override
  String get e7SetupCheckRestart =>
      'Comprobando el servidor local antes de reiniciar';

  @override
  String get e7SetupStoppingLocal => 'Deteniendo el servidor local';

  @override
  String get e7SetupStoppedLocal => 'Servidor local detenido';

  @override
  String get e7SetupUnknownSetup => 'Estado de configuración desconocido';

  @override
  String get e7SetupUnexpectedStop =>
      'El servidor local de OpenCode se detuvo de forma inesperada';

  @override
  String get e7SetupSetupInterrupted =>
      'La configuración se detuvo de forma inesperada; consulta la salida en vivo para ver los detalles';

  @override
  String get e7SetupRecoveryDisabled => 'Recuperación automática desactivada';

  @override
  String get e7SetupRecoveryWasDisabled =>
      'La recuperación automática estaba desactivada';

  @override
  String get e7SetupPortBusy =>
      'El puerto del servidor local sigue en uso; no se inició ningún reemplazo';

  @override
  String get e7SetupCredentialMismatch =>
      'La credencial guardada del servidor difiere de este runtime; restaura su credencial guardada original antes de volver';

  @override
  String get e7SetupUbuntuUnavailable =>
      'El entorno de Ubuntu administrado no está disponible';

  @override
  String get e7SetupRuntimeUnavailable =>
      'El comando de OpenCode seleccionado no está disponible';

  @override
  String get e7SetupIdentityMismatch =>
      'El proceso rastreado no es el servidor OpenCode administrado';

  @override
  String get e7SetupPasswordMissing => 'Falta la contraseña del servidor local';

  @override
  String get e7SetupVersionMissing =>
      'OpenCode se instaló, pero no informó ninguna versión';

  @override
  String get e7SetupModelsRefreshFailed =>
      'OpenCode se actualizó, pero no se pudo actualizar su catálogo de modelos';

  @override
  String get e7SetupStartupExited =>
      'El servidor de OpenCode terminó durante el inicio';

  @override
  String get e7SetupReadinessTimeout =>
      'El servidor de OpenCode no quedó autenticado y listo en 30 segundos';

  @override
  String get e7SetupUnreadableData =>
      'No se puede leer el registro de ubicación de datos de OpenCode 2';

  @override
  String get e7SetupUnreadablePrevious =>
      'No se puede leer el registro del runtime anterior';

  @override
  String get e7SetupReadManagerFailed =>
      'No se pudo leer el estado del administrador de configuración';

  @override
  String get e7SetupMissingManager => 'Falta el administrador de configuración';

  @override
  String get e7SetupRemoveInterruptedFailed =>
      'No se pudo quitar la instalación de Ubuntu interrumpida que pertenece a la app';

  @override
  String get e7SetupCheckStorageFailed =>
      'No se pudo comprobar el almacenamiento disponible antes de la configuración';

  @override
  String get e7SetupReadStorageFailed =>
      'No se pudo leer el almacenamiento disponible antes de la configuración';

  @override
  String get e7SetupUbuntuUnusable =>
      'Un contenedor de Ubuntu existente no se puede usar; la configuración no lo borrará';

  @override
  String get e7SetupExtractionFailed =>
      'La extracción de Ubuntu Base no creó un contenedor utilizable';

  @override
  String get e7SetupLockFailed =>
      'El administrador de configuración no pudo obtener su bloqueo de inicio';

  @override
  String get e7SetupSetupGroupFailed =>
      'El administrador de configuración no se inició en un grupo de procesos aislado';

  @override
  String get e7SetupSwitchGroupFailed =>
      'El administrador de cambio no se inició en un grupo de procesos aislado';

  @override
  String get e7SetupServerGroupFailed =>
      'El servidor administrado no se inició en un grupo de procesos aislado';

  @override
  String get e7SetupRecordIdentityFailed =>
      'No se pudo registrar la identidad del proceso del servidor administrado';

  @override
  String e7SetupConnectingProfile(String name) {
    return 'Conectando con $name';
  }

  @override
  String e7SetupConnectingAttempt(int attempt) {
    return 'Reconectando (intento $attempt)';
  }

  @override
  String get e7SetupOpeningWorkspace => 'Abriendo tu proyecto guardado.';

  @override
  String get e7SetupWhatToCheck => 'Qué revisar';

  @override
  String get e7SetupUpdatePassword => 'Actualizar contraseña';

  @override
  String e7SetupProbeHttp(String status) {
    return 'La dirección respondió, pero no como un servidor OpenCode (HTTP $status). Comprueba que la URL apunte a opencode serve.';
  }

  @override
  String e7SetupProbeError(String detail) {
    return 'Falló la prueba de conexión: $detail';
  }

  @override
  String e7SetupServerExit(String code) {
    return 'El servidor de OpenCode terminó (código $code)';
  }

  @override
  String get e7SetupSetupQueued => 'Configuración en cola';

  @override
  String get e7SetupNoSetup => 'No se ha iniciado ninguna configuración';

  @override
  String get e7SetupManagerMissingAfterLaunch =>
      'Falta el administrador de configuración tras el inicio';

  @override
  String get e7SetupBootstrapCleared => 'Estado de bootstrap borrado';

  @override
  String get e7SetupInstallingBeta => 'Instalando OpenCode 2';

  @override
  String get e7SetupAuthenticationFailed => 'Falló la autenticación';

  @override
  String get e7SetupUnknownVersion => 'versión desconocida';

  @override
  String get e7ModelUiClose => 'Cerrar el selector de modelos';

  @override
  String get e7ModelUiClearSearch => 'Borrar la búsqueda de modelos';

  @override
  String get e7ModelUiLoadFailed => 'No se pudieron cargar los modelos';

  @override
  String get e7ModelUiRetry => 'Intentar de nuevo';

  @override
  String get e7ModelUiBasicCatalog =>
      'Este servidor devolvió un catálogo básico. No hay detalles de capacidades ni de contexto.';

  @override
  String get e7ModelUiEditFilters => 'Editar los filtros de modelos';

  @override
  String get e7ModelUiFilterModels => 'Filtrar modelos';

  @override
  String get e7ModelUiFiltered => 'Filtrado';

  @override
  String get e7ModelUiFilters => 'Filtros';

  @override
  String get e7ModelUiRefresh => 'Actualizar modelos';

  @override
  String get e7ModelUiAnyCapability => 'Cualquier capacidad';

  @override
  String get e7ModelUiFastModes => 'Modos rápidos';

  @override
  String get e7ModelUiReasoning => 'Razonamiento';

  @override
  String get e7ModelUiLargestContext => 'Mayor contexto';

  @override
  String get e7ModelUiNoneAvailable => 'No hay modelos disponibles';

  @override
  String get e7ModelUiFavoritesEmpty => 'Guarda aquí tus modelos favoritos';

  @override
  String get e7ModelUiRecentEmpty => 'Tu próxima elección empieza aquí';

  @override
  String get e7ModelUiNoMatches => 'No hay modelos que coincidan';

  @override
  String get e7ModelUiConfigureProvider =>
      'Configura un proveedor en el servidor de OpenCode y actualiza.';

  @override
  String get e7ModelUiFavoritesHint =>
      'Toca la estrella junto a un modelo para encontrarlo aquí.';

  @override
  String get e7ModelUiRecentHint =>
      'Los modelos que uses aparecerán aquí, los más recientes primero.';

  @override
  String get e7ModelUiNoFastModes =>
      'Ningún modelo indica un modo rápido o de bajo esfuerzo explícito.';

  @override
  String get e7ModelUiNoMatchesHint =>
      'Prueba con otra búsqueda, proveedor o filtro de capacidad.';

  @override
  String get e7ModelUiClearFilters => 'Quitar filtros';

  @override
  String get e7ModelUiBrowseAll => 'Ver todos los modelos';

  @override
  String get e7ModelUiAgent => 'Agente';

  @override
  String get e7ModelUiServerDefault => 'Predeterminado del servidor';

  @override
  String get e7ModelUiProvider => 'Proveedor';

  @override
  String get e7ModelUiAllProviders => 'Todos los proveedores';

  @override
  String get e7ModelUiCurrent => 'Modelo actual';

  @override
  String get e7ModelUiUnavailable => 'No disponible';

  @override
  String get e7ModelUiDeprecated => 'Obsoleto';

  @override
  String get e7ModelUiPreview => 'Vista previa';

  @override
  String get e7ModelUiFavoritesFailed =>
      'No se pudieron guardar los favoritos. Inténtalo de nuevo.';

  @override
  String e7ModelUiUseModelMode(String model, String agent) {
    return 'Usar $model · $agent';
  }

  @override
  String get e7ModelUiUseSession => 'Usar en esta conversación';

  @override
  String get e7ModelUiUseNewSessions => 'Usar en conversaciones nuevas';

  @override
  String get e7ModelUiTools => 'Herramientas';

  @override
  String get e7ModelUiAttachments => 'Adjuntos';

  @override
  String get e7ModelUiDefault => 'Predeterminado';

  @override
  String get e7ModelUiSelectionGone =>
      'Esta opción ya no está disponible. Actualiza los modelos e inténtalo de nuevo.';

  @override
  String get e7VoiceUiLocalInput => 'Entrada de voz local';

  @override
  String get e7VoiceUiChooseModel => 'Elige un modelo multilingüe Whisper INT8';

  @override
  String get e7VoiceUiPrivacyDownload =>
      'El audio nunca sale de este teléfono.';

  @override
  String get e7VoiceUiNoBuiltInMic =>
      'Android reporta que no hay micrófono integrado. La entrada de voz puede funcionar con un micrófono cableado o USB.';

  @override
  String get e7VoiceUiLanguage => 'Idioma de transcripción';

  @override
  String get e7VoiceUiVerifying => 'Verificando el modelo descargado';

  @override
  String get e7VoiceUiCancelDownload => 'Cancelar la descarga';

  @override
  String get e7VoiceUiNotNow => 'Ahora no';

  @override
  String get e7VoiceUiUseModel => 'Usar el modelo';

  @override
  String get e7VoiceUiDownload => 'Descargar';

  @override
  String get e7VoiceUiKeep => 'Conservar';

  @override
  String get e7VoiceUiDelete => 'Eliminar';

  @override
  String get e7VoiceUiDefaultBadge => 'por defecto';

  @override
  String get e7VoiceUiOptionalBadge => 'opcional';

  @override
  String get e7VoiceUiInstalledBadge => 'instalado';

  @override
  String get e7VoiceUiNotInstalledBadge => 'no instalado';

  @override
  String get e7VoiceUiSetupBusy =>
      'No disponible mientras la configuración del modelo está en curso';

  @override
  String get e7VoiceUiSelected => 'Seleccionado';

  @override
  String get e7VoiceUiSelectHint => 'Doble toque para seleccionar';

  @override
  String get e7VoiceUiDefault => 'Por defecto';

  @override
  String get e7VoiceUiOptional => 'Opcional';

  @override
  String get e7VoiceUiInstalled => 'Instalado';

  @override
  String get e7VoiceUiRedownload => 'Volver a descargar';

  @override
  String get e7VoiceUiOpenSettings => 'Abrir los ajustes de la app';

  @override
  String get e7VoiceUiRetry => 'Reintentar';

  @override
  String get e7VoiceUiStartListening => 'Empezar a escuchar';

  @override
  String get e7VoiceUiDraftReady => 'Transcripción lista para revisar';

  @override
  String get e7VoiceUiNeedsAttention =>
      'La entrada de voz requiere tu atención';

  @override
  String get e7VoiceUiModelRequired => 'Se requiere un modelo local';

  @override
  String e7ModelUiCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count modelos',
      one: '1 modelo',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiContext(String count) {
    return '$count de contexto';
  }

  @override
  String e7ModelUiOutput(String count) {
    return '$count de salida';
  }

  @override
  String e7ModelUiFavorite(String model) {
    return 'Marcar $model como favorito';
  }

  @override
  String e7ModelUiUnfavorite(String model) {
    return 'Quitar $model de favoritos';
  }

  @override
  String e7ModelUiEffort(String variant, String effort) {
    return '$variant · esfuerzo $effort';
  }

  @override
  String get e7ModelUiLoading => 'Cargando el catálogo de modelos';

  @override
  String e7ModelUiCost(String input, String output) {
    return '$input entrada · $output salida /1M';
  }

  @override
  String e7VoiceUiDownloadPercent(int percent) {
    return 'Descargando el modelo de voz, $percent por ciento';
  }

  @override
  String e7VoiceUiDownloadProgress(String received, String total) {
    return '$received de $total';
  }

  @override
  String e7VoiceUiSetupFailed(String error) {
    return 'Falló la configuración del modelo: $error';
  }

  @override
  String e7VoiceUiDeletePack(String model) {
    return '¿Eliminar el modelo de voz de $model?';
  }

  @override
  String e7VoiceUiDeleteDetail(String size) {
    return 'Esto libera $size del almacenamiento privado de la app. Puedes volver a descargarlo después.';
  }

  @override
  String e7VoiceUiDownloadSize(String size) {
    return 'Descarga de $size';
  }

  @override
  String e7VoiceUiPackSemantics(
    String model,
    String size,
    String badges,
    String description,
  ) {
    return '$model, $size, $badges. $description';
  }

  @override
  String get e7VoiceUiLicenses => 'Licencias y procedencia de la voz';

  @override
  String get e7VoiceUiNoticesFailed =>
      'No se pudieron cargar las licencias de voz. Reintenta.';

  @override
  String get e7VoiceUiAuto => 'Detección automática';

  @override
  String get e7VoiceUiEnglish => 'Inglés';

  @override
  String get e7VoiceUiArabic => 'Árabe';

  @override
  String get e7VoiceUiBalanced => 'Equilibrado';

  @override
  String get e7VoiceUiBalancedDetail =>
      'Relación recomendada entre calidad, almacenamiento y velocidad.';

  @override
  String get e7VoiceUiAccurate => 'Máxima precisión';

  @override
  String get e7VoiceUiAccurateDetail =>
      'La mejor calidad, opcional; exige bastante más memoria.';

  @override
  String get e7VoiceUiCompact => 'Compacto de respaldo';

  @override
  String get e7VoiceUiCompactDetail =>
      'El más rápido y pequeño; con menos precisión en audio difícil.';

  @override
  String get e7VoiceUiUnsupportedAbi =>
      'Ningún runtime de voz incluido admite el ABI de este dispositivo.';

  @override
  String get e7VoiceUiPermissionBlocked =>
      'El acceso al micrófono está bloqueado. Permítelo en los ajustes de la app de Android.';

  @override
  String get e7VoiceUiPermissionRequired =>
      'Se requiere el permiso de micrófono para la entrada de voz local.';

  @override
  String get e7VoiceUiDeviceUnavailable =>
      'La entrada de voz local no está disponible. Detén la reproducción, revisa los ajustes del micrófono y reintenta.';

  @override
  String get e7VoiceUiInputUnavailable =>
      'La entrada de voz local no está disponible en esta plataforma.';

  @override
  String get e7VoiceUiNoAudio => 'No se capturó ningún audio.';

  @override
  String get e7VoiceUiInterrupted => 'La grabación se interrumpió.';

  @override
  String get e7VoiceUiMicrophoneError =>
      'El micrófono reportó un error. Revisa sus ajustes y reintenta.';

  @override
  String get e7VoiceUiInputFailed =>
      'La entrada de voz no pudo terminar. Reintenta.';

  @override
  String get e7VoiceUiTechnicalDetails => 'Detalles técnicos';

  @override
  String get e7VoiceUiHttpsRequired =>
      'Las descargas de modelos de voz solo pueden recibirse sobre HTTPS.';

  @override
  String get e7VoiceUiTransportClosed =>
      'El transporte de descarga de voz está cerrado.';

  @override
  String get e7VoiceUiInvalidRedirect =>
      'La descarga del modelo de voz devolvió una redirección inválida.';

  @override
  String get e7VoiceUiUnsafeRedirect =>
      'La descarga del modelo de voz redirigió a una URL que no es HTTPS.';

  @override
  String get e7VoiceUiNoResponse =>
      'El servidor de modelos no devolvió respuesta.';

  @override
  String get e7VoiceUiDownloadTimeout =>
      'La descarga del modelo agotó su tiempo. Revisa la conexión y reintenta.';

  @override
  String get e7VoiceUiChecksumFailed =>
      'El modelo descargado falló en la verificación del checksum. Vuelve a descargarlo.';

  @override
  String get e7VoiceUiVerificationFailed =>
      'El modelo falló la verificación final. Vuelve a descargarlo.';

  @override
  String get e7VoiceUiHttpFailed =>
      'El servidor de modelos rechazó la descarga. Reintenta.';

  @override
  String get e7VoiceUiLengthFailed =>
      'La descarga del modelo tiene un tamaño inesperado. Vuelve a descargarla.';

  @override
  String get e7VoiceUiIncomplete =>
      'La descarga del modelo está incompleta. Reintenta.';

  @override
  String get e7VoiceUiDownloadFailed =>
      'No se pudo descargar el modelo de voz. Reintenta.';

  @override
  String e7VoiceUiMemory(String model, int required, int available) {
    return '$model necesita un teléfono con cerca de $required MB de memoria; este tiene $available MB.';
  }

  @override
  String e7VoiceUiStorage(String model, String size) {
    return '$model necesita $size libres, con margen de seguridad incluido.';
  }

  @override
  String get e7ModelUiProviderFallback => 'un proveedor';

  @override
  String e7ModelUiProviderPair(String first, String last) {
    return '$first y $last';
  }

  @override
  String e7ModelUiProviderMany(String first, String last) {
    return '$first y $last';
  }

  @override
  String get e7ModelUiListSeparator => ', ';

  @override
  String e7ModelUiUnloadedProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Sesión iniciada en $providers, pero el servidor aún no los ha cargado, así que sus modelos no pueden responder.',
      one:
          'Sesión iniciada en $providers, pero el servidor aún no lo ha cargado, así que sus modelos no pueden responder.',
    );
    return '$_temp0';
  }

  @override
  String get e7SharedOpenCodeUnreachableTryAgain =>
      'No se puede acceder a OpenCode. Inténtalo de nuevo.';

  @override
  String get approvalsUiMenu => 'Aprobaciones';

  @override
  String get approvalsUiInheritedFrom =>
      'Heredado de la conversación principal';

  @override
  String get approvalsUiFollowParent => 'Volver a seguir a la principal';

  @override
  String get approvalsUiIndicatorOn => 'Aprobando automáticamente';

  @override
  String approvalsUiAutoApproved(String action) {
    return 'Aprobado automáticamente · $action';
  }

  @override
  String get approvalsUiFailedDetail =>
      'Falló la aprobación automática. Revisa esta solicitud.';

  @override
  String approvalsUiSaveFailed(String error) {
    return 'No se pudo guardar el ajuste de aprobación: $error';
  }

  @override
  String get approvalsUiIndicatorPaused => 'Aprobación automática en pausa';

  @override
  String get handoffUiComputerTitle => 'Continuar en la computadora';

  @override
  String handoffUiComputerIntro(String binary) {
    return 'Ejecuta esto en una terminal de la computadora que corre este servidor. Abre la misma conversación en la interfaz $binary. Nada se envía hasta que escribas.';
  }

  @override
  String get handoffUiComputerDirectoryNote =>
      'Las conversaciones pertenecen a una carpeta de proyecto, así que el comando primero entra en la carpeta de esta conversación.';

  @override
  String handoffUiComputerVerify(String verified, String binary) {
    return 'Verificado con $verified. Si tu versión instalada difiere, revisa $binary --help para la etiqueta --session.';
  }

  @override
  String get handoffUiUnavailableDirectory =>
      'El servidor no reportó una carpeta de proyecto para esta conversación, así que no hay carpeta donde abrirla. Recarga la conversación y reintenta.';

  @override
  String get handoffUiUnavailableWorkspace =>
      'Esta conversación corre dentro de un entorno en la nube. Su carpeta pertenece al host del entorno, así que un comando de terminal común no puede abrirla. En su lugar, exporta e importa la conversación.';

  @override
  String get handoffUiUnavailableReference =>
      'La referencia de esta conversación no se puede poner en un comando con seguridad.';

  @override
  String get handoffUiPhoneTitle => 'Abrir en otro teléfono';

  @override
  String get handoffUiPhoneIntro =>
      'Escanéalo con OpenCode Mobile en el otro teléfono. El código contiene solo los IDs del servidor y de la conversación.';

  @override
  String get handoffUiPhoneQrLabel =>
      'Código QR que abre esta conversación en otro teléfono';

  @override
  String get handoffUiPhoneLinkLabel => 'Enlace';

  @override
  String get handoffUiPhoneCopyLink => 'Copiar el enlace';

  @override
  String get handoffUiPhoneUnavailable =>
      'No se puede construir un enlace para esta conversación. Recarga la conversación y reintenta.';

  @override
  String get handoffUiLinkServerMissing =>
      'La conversación está en un servidor que este teléfono no tiene guardado. Agrégalo aquí y escanea el código otra vez.';

  @override
  String get handoffUiLinkDismiss => 'Omitir';

  @override
  String get handoffUiLinkWaiting =>
      'Abriendo la conversación cuando el servidor se conecte…';

  @override
  String get handoffUiLinkReentry =>
      'Escribe de nuevo la contraseña de este servidor y escanea el código otra vez.';

  @override
  String get handoffUiLinkConnectionFailed =>
      'No se pudo conectar al servidor guardado. Revísalo en Servidores y escanea el código otra vez.';

  @override
  String get teamUiAccessControls => 'Decisiones y controles';

  @override
  String get teamUiAccessReadOnly => 'Solo lectura';

  @override
  String get teamUiAddAddressHint => 'http://100.x.x.x:8373';

  @override
  String get teamUiAddAddressLabel => 'Dirección';

  @override
  String get teamUiAddManually => 'Agregar manualmente';

  @override
  String get teamUiAddSubmit => 'Probar y activar';

  @override
  String get teamUiAddTesting => 'Revisando la dirección…';

  @override
  String get teamUiAddTitle => 'Agregar host de AI Team';

  @override
  String get teamUiAddressRequired => 'Escribe la dirección del host.';

  @override
  String get teamUiChange => 'Cambiar';

  @override
  String get teamUiDisclaimerComputer =>
      'Corre tan rápido como tu computadora; mantenla despierta';

  @override
  String get teamUiDisclaimerPhone =>
      'Android puede detenerlo con la pantalla apagada; más lento que en una computadora';

  @override
  String get teamUiEditorBody =>
      'Si esta computadora ejecuta Gas City, la app puede encontrarla automáticamente.';

  @override
  String teamUiEditorConfigured(String url) {
    return 'Host de AI Team: $url';
  }

  @override
  String get teamUiEditorTitle => 'AI Team (opcional)';

  @override
  String get teamUiHostGuideIntro =>
      'Todo se queda en tu red de Tailscale; nada se publica en el internet.';

  @override
  String get teamUiHostGuideStep1 =>
      'Instala las tres herramientas de Gas City — gc, bd y dolt — en tu PATH; la guía completa trae cada descarga con su checksum. Luego revisa que se encuentren las tres:';

  @override
  String get teamUiHostGuideStep2 =>
      'Guarda el archivo del equipo de la guía completa en una carpeta junto a tu proyecto. Después configura el equipo y agrega tu proyecto, primero la carpeta:';

  @override
  String get teamUiHostGuideStep3 => 'Inicia el equipo y revisa que responda:';

  @override
  String get teamUiHostGuideStep4 =>
      'Descarga el frente que permite la entrada de este teléfono por Tailscale, revísalo e inícialo, con tu propio inicio de Tailscale después de --allow. Luego agrégalo aquí: la dirección Tailscale de la computadora con el puerto del comando, y el nombre del equipo.';

  @override
  String get teamUiHostGuideTitle =>
      'Hacer correr un equipo de IA en tu computadora';

  @override
  String get teamUiHostModeComputer => 'Computadora';

  @override
  String get teamUiHostModePhone => 'Este teléfono';

  @override
  String get teamUiHow => 'Cómo';

  @override
  String get teamUiKeep => 'Conservar';

  @override
  String get teamUiLabelAccess => 'Acceso';

  @override
  String get teamUiLabelAddress => 'Dirección';

  @override
  String get teamUiLabelCity => 'Ciudad';

  @override
  String get teamUiLabelHost => 'Host';

  @override
  String get teamUiLabelProvider => 'Proveedor';

  @override
  String get teamUiLabelVersion => 'Versión';

  @override
  String get teamUiLearnHow => 'Más información';

  @override
  String get teamUiPluginsTitle => 'Plugins';

  @override
  String get teamUiReadOnlyBody =>
      'Puedes observar este equipo desde el teléfono. Responder y dirigir requieren el frente en la computadora.';

  @override
  String get teamUiReasonCityNotRunning => 'host del equipo iniciando';

  @override
  String get teamUiReasonNotGasCity => 'no se encontró ningún equipo de IA';

  @override
  String get teamUiReasonPlainHttp => 'la dirección no está en Tailscale';

  @override
  String get teamUiReasonReadFailed => 'la última lectura falló';

  @override
  String get teamUiReasonUnreachable => 'host inalcanzable';

  @override
  String get teamUiRefresh => 'Actualizar';

  @override
  String get teamUiRowConnecting => 'Activo · conectando…';

  @override
  String get teamUiRowNotAvailable => 'No disponible en este servidor';

  @override
  String teamUiRowNotAvailableReason(String reason) {
    return 'No disponible en este servidor · $reason';
  }

  @override
  String get teamUiRowOff => 'Apagado';

  @override
  String teamUiRowOn(String server) {
    return 'Activo · $server';
  }

  @override
  String teamUiRowOnReadOnly(String server) {
    return 'Activo · $server · solo vista';
  }

  @override
  String get teamUiRowReconnecting => 'Activo · reconectando…';

  @override
  String get teamUiRowTitle => 'AI Team · Gas City';

  @override
  String teamUiRowUnreachable(String minutes) {
    return 'Activo · host inalcanzable desde hace $minutes min';
  }

  @override
  String get teamUiTailnetRequired =>
      'AI Team funciona sobre tu red de Tailscale o en este dispositivo. Usa la dirección Tailscale de la computadora (100.x.x.x o nombre.ts.net).';

  @override
  String get teamUiTechnicalDetails => 'Detalles técnicos';

  @override
  String get teamUiTechnicalLastAnswer => 'Última respuesta del host';

  @override
  String get teamUiTermAgent => 'Agente · polecat';

  @override
  String get teamUiTermProject => 'Proyecto · rig';

  @override
  String get teamUiTermRun => 'Ejecución · convoy';

  @override
  String get teamUiTermTeam => 'Equipo · city';

  @override
  String get teamUiTermWork => 'Trabajo · bead';

  @override
  String get teamUiTermsHeading => 'Términos';

  @override
  String get teamUiTurnOffBody =>
      'Quita su tarjeta, los elementos de atención y los datos del equipo en caché de este teléfono. Nada cambia en el host.';

  @override
  String get teamUiTurnOffConfirm => 'Apagar AI Team';

  @override
  String teamUiTurnOffTitle(String server) {
    return '¿Apagar AI Team para $server?';
  }

  @override
  String get teamUiVerdictCityNotRunning =>
      'El host del equipo está iniciando. Reintenta en un momento.';

  @override
  String get teamUiVerdictNotGasCity =>
      'Este servidor aún no hace correr un equipo de IA. Configura uno en la computadora — toma unos minutos.';

  @override
  String get teamUiVerdictUnreachable =>
      'Ninguna respuesta de esta dirección. Revísala, y que la computadora esté despierta y en tu red de Tailscale.';

  @override
  String get teamUiVersionUnknown => 'desconocida';

  @override
  String teamUiCardAgentsSummary(
    int total,
    int working,
    int waiting,
    int idle,
    int stopped,
  ) {
    return '$total agentes: $working trabajando, $waiting esperando, $idle inactivos, $stopped detenidos';
  }

  @override
  String get teamUiCardEmptyHint =>
      'Por ahora, lanza las tareas desde la computadora.';

  @override
  String get teamUiCardEmptyTitle => 'No hay tareas recientes';

  @override
  String get teamUiCardErrorCityNotRunning =>
      'El host del equipo está iniciando. Reintenta en un momento.';

  @override
  String get teamUiCardErrorNotGasCity =>
      'Este servidor aún no hace correr un equipo de IA. Configura uno en la computadora — toma unos minutos.';

  @override
  String get teamUiCardErrorPlainHttp =>
      'AI Team funciona sobre tu red de Tailscale o en este dispositivo. Usa tailscale serve en la computadora y reintenta.';

  @override
  String get teamUiCardErrorUnreachable =>
      'El host del equipo no se puede alcanzar. AI Team funciona sobre tu red de Tailscale o en este dispositivo.';

  @override
  String get teamUiStateUnreachableTitle =>
      'No se puede alcanzar el host del equipo';

  @override
  String get teamUiStateNotGasCityTitle =>
      'No hay equipo de IA en este servidor';

  @override
  String get teamUiStateStartingTitle => 'El host del equipo está iniciando';

  @override
  String get teamUiStatePlainHttpTitle =>
      'AI Team no puede usar esta dirección';

  @override
  String get teamUiStateNotAnsweringTitle => 'El equipo no responde';

  @override
  String get teamUiCardLoading => 'Conectando al host del equipo…';

  @override
  String teamUiCardRefreshFailed(String time) {
    return 'La última actualización falló · se muestran datos de $time';
  }

  @override
  String get teamUiCardRetry => 'Reintentar';

  @override
  String get teamUiCardRunStateBlocked => 'Bloqueado';

  @override
  String get teamUiCardRunStateCancelled => 'Cancelado';

  @override
  String get teamUiCardRunStateCompleted => 'Listo';

  @override
  String get teamUiCardRunStateFailed => 'Falló';

  @override
  String get teamUiCardRunStatePlanning => 'Planificando';

  @override
  String get teamUiCardRunStateUnknown => 'Desconocido';

  @override
  String get teamUiCardRunStateWaiting => 'Esperando a un ejecutor';

  @override
  String get teamUiCardRunStateWorking => 'Trabajando';

  @override
  String get teamUiCardRunStateWaitingMerge => 'Revisando';

  @override
  String get teamUiCardRunStateMerged => 'Listo · fusionado';

  @override
  String teamUiCardStale(String time) {
    return 'Se muestran datos de $time · host inalcanzable';
  }

  @override
  String get teamUiHomeAgentNoWork => 'Sin trabajo actual';

  @override
  String get teamUiHomeAgentStateBlocked => 'Bloqueado';

  @override
  String get teamUiHomeAgentStateCrashed => 'Se cae';

  @override
  String get teamUiHomeAgentStateIdle => 'Inactivo';

  @override
  String get teamUiHomeAgentStateStopped => 'Detenido';

  @override
  String get teamUiHomeAgentStateUnknown => 'Desconocido';

  @override
  String get teamUiHomeAgentStateWaiting => 'Esperándote';

  @override
  String get teamUiHomeAgentStateWorking => 'Trabajando';

  @override
  String get teamUiHomeFilterActive => 'Activos';

  @override
  String get teamUiHomeFilterAll => 'Todos';

  @override
  String get teamUiHomeFilterBlocked => 'Bloqueados';

  @override
  String get teamUiHomeFilterCompleted => 'Listas';

  @override
  String get teamUiHomeGateAnswerOnComputer =>
      'Responde esto en la computadora. Por ahora el teléfono solo puede observar.';

  @override
  String get teamUiHomeGateAnswerOnPhone =>
      'Responde esto en el host de este teléfono. Por ahora la app solo puede observar.';

  @override
  String get teamUiHomeGateKindChoice => 'Decisión';

  @override
  String get teamUiHomeGateKindConfirmation => 'Aprobación';

  @override
  String get teamUiHomeGateKindFreeText => 'Pregunta';

  @override
  String get teamUiHomeGateKindGateBead => 'Compuerta';

  @override
  String get teamUiHomeGateKindReviewReady => 'Revisión lista';

  @override
  String get teamUiHomeGateKindRunFailed => 'Ejecución fallida';

  @override
  String get teamUiHomeGateKindUnknown => 'Requiere tu atención';

  @override
  String teamUiHomeGateLinkAgent(String name) {
    return 'Agente $name';
  }

  @override
  String teamUiHomeGateLinkRun(String title) {
    return 'Tarea $title';
  }

  @override
  String teamUiHomeGateLinkWork(String title) {
    return 'Trabajo $title';
  }

  @override
  String get teamUiHomeGateOptions => 'Opciones';

  @override
  String get teamUiHomeHostRawHeading => 'Valores en bruto';

  @override
  String get teamUiHomeRunNeedsYou => 'Requiere tu atención';

  @override
  String teamUiHomeRunProgress(int done, int total) {
    return '$done de $total listas';
  }

  @override
  String get teamUiHomeRunsEmptyFiltered => 'Ninguna tarea coincide.';

  @override
  String get teamUiHomeRunsEmptyHint =>
      'Prueba otro filtro o vacía la búsqueda.';

  @override
  String get teamUiHomeSearchHint => 'Buscar tareas';

  @override
  String get teamUiHomeTitle => 'AI Team';

  @override
  String get teamUiRunBack => 'Atrás';

  @override
  String teamUiRunBlockedByDeps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'esperando $count pasos más',
      one: 'esperando un paso más',
    );
    return '$_temp0';
  }

  @override
  String teamUiRunElapsedDays(int count) {
    return '$count d';
  }

  @override
  String teamUiRunElapsedHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String teamUiRunElapsedMinutes(int count) {
    return '$count min';
  }

  @override
  String teamUiRunSinceHandoff(String elapsed) {
    return '$elapsed desde la entrega';
  }

  @override
  String get teamUiRunLabelFormula => 'Fórmula';

  @override
  String get teamUiRunLabelId => 'ID de ejecución';

  @override
  String get teamUiRunLabelKind => 'Clase';

  @override
  String get teamUiRunLabelLastError => 'Último error';

  @override
  String get teamUiRunLabelProject => 'Proyecto';

  @override
  String get teamUiRunLabelRawState => 'Estado del proveedor';

  @override
  String get teamUiRunLabelStarted => 'Iniciada';

  @override
  String get teamUiRunLabelTrackedWork => 'Trabajo registrado';

  @override
  String get teamUiRunLabelUpdated => 'Actualizada';

  @override
  String get teamUiRunMissingHint =>
      'Puede que se haya cerrado o eliminado. Actualiza para revisar otra vez.';

  @override
  String get teamUiRunMissingTitle => 'Esta tarea ya no está en el host';

  @override
  String get teamUiRunTermBatch => 'Tarea · convoy';

  @override
  String get teamUiRunTermFormula => 'Tarea · fórmula';

  @override
  String get teamUiRunTermUnknown => 'Tarea';

  @override
  String teamUiRunTimelineAgentStopped(String name) {
    return '$name se detuvo';
  }

  @override
  String teamUiRunTimelineAgentWoke(String name) {
    return '$name arrancó';
  }

  @override
  String teamUiRunTimelineGateOpened(String title) {
    return 'Requiere tu atención: $title';
  }

  @override
  String teamUiRunTimelineGateResolved(String title) {
    return 'Respondido: $title';
  }

  @override
  String teamUiRunTimelineRunChanged(String state) {
    return 'La ejecución ahora está $state';
  }

  @override
  String teamUiRunTimelineWorkClosed(String title) {
    return '$title cerrado';
  }

  @override
  String teamUiRunTimelineWorkCreated(String title) {
    return '$title agregado';
  }

  @override
  String teamUiRunTimelineWorkUpdated(String title) {
    return '$title actualizado';
  }

  @override
  String teamUiAgentContextSemantics(int percent) {
    return 'Contexto usado al $percent%.';
  }

  @override
  String teamUiAgentContextShort(int percent) {
    return 'cxt $percent%';
  }

  @override
  String get teamUiAgentLabelBranch => 'Rama';

  @override
  String get teamUiAgentLabelHarness => 'Harness';

  @override
  String get teamUiAgentLabelModel => 'Modelo';

  @override
  String get teamUiAgentLabelPack => 'Paquete';

  @override
  String get teamUiAgentLabelPool => 'Pool';

  @override
  String get teamUiAgentLabelSessionAge => 'Edad de la sesión';

  @override
  String get teamUiAgentLabelSessionId => 'Sesión';

  @override
  String get teamUiAgentLabelSessionName => 'Nombre de la sesión';

  @override
  String get teamUiAgentLabelWorkDir => 'Directorio de trabajo';

  @override
  String get teamUiAgentMissingHint =>
      'Puede que se haya reciclado. Actualiza para revisar.';

  @override
  String get teamUiAgentMissingTitle => 'Este agente ya no está en el host';

  @override
  String get teamUiAgentNeedsYou => 'Requiere tu atención';

  @override
  String get teamUiAgentOutputConnecting => 'Conectando a la sesión…';

  @override
  String get teamUiAgentOutputCopy => 'Copiar la salida';

  @override
  String get teamUiAgentOutputEnded =>
      'Sesión terminada · la salida ya no está en el host';

  @override
  String get teamUiAgentOutputJump => 'Saltar a lo más reciente';

  @override
  String get teamUiAgentOutputLive => 'En vivo';

  @override
  String get teamUiAgentOutputTitle => 'Salida en vivo';

  @override
  String get teamUiAgentOutputUnavailable =>
      'La salida en vivo no está disponible para este agente';

  @override
  String get teamUiAgentRecyclingSoon =>
      'Reciclaje pronto · el contexto está por llenarse';

  @override
  String teamUiAgentSessionAge(String age) {
    return 'Sesión $age';
  }

  @override
  String teamUiAgentTermSession(String id) {
    return 'Agente · sesión $id';
  }

  @override
  String get teamUiAgentValueUnknown => 'No reportado';

  @override
  String get teamUiWorkLabelAssignee => 'Responsable';

  @override
  String get teamUiWorkLabelClosedReason => 'Motivo de cierre';

  @override
  String get teamUiWorkLabelDependsOn => 'Depende de (IDs)';

  @override
  String get teamUiWorkLabelId => 'ID del trabajo';

  @override
  String get teamUiWorkLabelLabels => 'Etiquetas';

  @override
  String get teamUiWorkLabelParent => 'Padre';

  @override
  String get teamUiWorkLabelProject => 'Proyecto';

  @override
  String get teamUiWorkLabelRawState => 'Estado del proveedor';

  @override
  String get teamUiWorkLabelRun => 'ID de ejecución';

  @override
  String get teamUiWorkLabelSession => 'ID de sesión';

  @override
  String get teamUiWorkLabelSessionName => 'Nombre de la sesión';

  @override
  String get teamUiWorkLabelType => 'Clase';

  @override
  String get teamUiWorkOwnerNone => 'Sin asignar';

  @override
  String get teamUiWorkSheetBlocking => 'Bloquea';

  @override
  String get teamUiWorkSheetBranch => 'Rama';

  @override
  String get teamUiWorkSheetClosed => 'Cerrado';

  @override
  String get teamUiWorkSheetCreated => 'Creado';

  @override
  String get teamUiWorkSheetDependencies => 'Depende de';

  @override
  String get teamUiWorkSheetDescription => 'Descripción';

  @override
  String get teamUiWorkSheetMissing =>
      'Este elemento de trabajo ya no está en el host.';

  @override
  String get teamUiWorkSheetNoTimestamps =>
      'El host no envió marcas de tiempo.';

  @override
  String get teamUiWorkSheetOpenSession => 'Abrir la sesión';

  @override
  String get teamUiWorkSheetOutput => 'Salida';

  @override
  String teamUiWorkSheetStamp(String date, String clock, String age) {
    return '$date · $clock ($age)';
  }

  @override
  String get teamUiWorkSheetTarget => 'Objetivo de fusión';

  @override
  String get teamUiWorkSheetTimestamps => 'Marcas de tiempo';

  @override
  String get teamUiWorkSheetUpdated => 'Actualizado';

  @override
  String get teamUiWorkSheetValidation => 'Validación';

  @override
  String get teamUiWorkSheetValidationFailed => 'Falló';

  @override
  String get teamUiWorkSheetValidationPassed => 'Pasó';

  @override
  String get teamUiWorkSheetValidationUnknown => 'Resultado registrado';

  @override
  String get teamUiWorkSheetWorktree => 'Árbol de trabajo';

  @override
  String get teamUiWorkStateBlocked => 'Bloqueado';

  @override
  String get teamUiWorkStateCancelled => 'Cancelado';

  @override
  String get teamUiWorkStateCompleted => 'Listo';

  @override
  String get teamUiWorkStateFailed => 'Falló';

  @override
  String get teamUiWorkStateNeedsInput => 'Necesita tu respuesta';

  @override
  String get teamUiWorkStateQueued => 'En fila';

  @override
  String get teamUiWorkStateReady => 'Listo';

  @override
  String get teamUiWorkStateReview => 'Revisión';

  @override
  String get teamUiWorkStateUnknown => 'Desconocido';

  @override
  String get teamUiWorkStateWaiting => 'Esperando';

  @override
  String get teamUiWorkStateWorking => 'Trabajando';

  @override
  String teamUiUsageCostEstimated(String cost) {
    return '$cost est.';
  }

  @override
  String teamUiUsageTokens(String count) {
    return '$count tokens';
  }

  @override
  String get teamUiGateAnswerOnHost =>
      'Responde esto en el host. Por ahora el teléfono solo puede observar.';

  @override
  String get teamUiGateAnswerOnHostPhone =>
      'Responde esto en el host de este teléfono. Por ahora la app solo puede observar.';

  @override
  String get teamUiGateCloseOnHost =>
      'Cierra esto en el host. Por ahora el teléfono solo puede observar.';

  @override
  String get teamUiGateCloseOnHostPhone =>
      'Cierra esto en el host de este teléfono. Por ahora la app solo puede observar.';

  @override
  String get teamUiGateDestructive => 'Destructivo';

  @override
  String get teamUiGateFailureActionAgent =>
      'Reinicia el agente en el host; recoge el elemento de trabajo otra vez.';

  @override
  String get teamUiGateFailureActionAuthentication =>
      'Inicia sesión otra vez en el host (clave o token del proveedor) y reintenta la ejecución.';

  @override
  String get teamUiGateFailureActionContext =>
      'Reinicia el agente con un contexto nuevo en el host; retoma el elemento de trabajo.';

  @override
  String get teamUiGateFailureActionDependency =>
      'Instala o actualiza la dependencia que falta en el host y reintenta la ejecución.';

  @override
  String get teamUiGateFailureActionExecution =>
      'Lee el registro del paso en el host, arregla el comando y reintenta la ejecución.';

  @override
  String get teamUiGateFailureActionInfrastructure =>
      'Revisa el host y sus servicios y reintenta la ejecución.';

  @override
  String get teamUiGateFailureActionMergeConflict =>
      'Resuelve el conflicto en el árbol de trabajo del host y reintenta la ejecución.';

  @override
  String get teamUiGateFailureActionTest =>
      'Corrige en el host las pruebas que fallan y reintenta la ejecución.';

  @override
  String get teamUiGateFailureActionUnknown =>
      'Lee el error en el host y decide ahí; el teléfono aún no puede actuar.';

  @override
  String get teamUiGateFailureAffectedNone =>
      'Ningún elemento de trabajo abierto de esta ejecución.';

  @override
  String get teamUiGateFailureAffectedWork => 'Trabajo afectado';

  @override
  String get teamUiGateFailureClassAgent => 'Agente';

  @override
  String get teamUiGateFailureClassAuthentication => 'Autenticación';

  @override
  String get teamUiGateFailureClassContext => 'Contexto';

  @override
  String get teamUiGateFailureClassDependency => 'Dependencia';

  @override
  String get teamUiGateFailureClassExecution => 'Ejecución';

  @override
  String get teamUiGateFailureClassInfrastructure => 'Infraestructura';

  @override
  String get teamUiGateFailureClassMergeConflict => 'Conflicto de fusión';

  @override
  String get teamUiGateFailureClassTest => 'Prueba';

  @override
  String get teamUiGateFailureClassUnknown => 'Desconocido';

  @override
  String get teamUiGateFailureClassification => 'Qué salió mal';

  @override
  String get teamUiGateFailureErrorNone => 'El host no envió texto de error.';

  @override
  String get teamUiGateGone =>
      'Esto ya no espera de ti; se respondió o se cerró en el host.';

  @override
  String get teamUiGateLabelKind => 'Clase de proveedor';

  @override
  String get teamUiGateLabelRequestId => 'ID de petición';

  @override
  String get teamUiGateLabelRunId => 'ID de ejecución';

  @override
  String get teamUiGateLabelSessionId => 'ID de sesión';

  @override
  String get teamUiGateLabelWorkId => 'ID del trabajo';

  @override
  String get teamUiGateNoDescription => 'El host no envió descripción.';

  @override
  String get teamUiGateReviewOnHost =>
      'Revisa esto en el host. Por ahora el teléfono solo puede observar.';

  @override
  String get teamUiGateReviewOnHostPhone =>
      'Revisa esto en el host de este teléfono. Por ahora la app solo puede observar.';

  @override
  String get teamUiGateUnblocks => 'Desactiva';

  @override
  String get teamUiGateUnblocksNone => 'Aún nada espera de esto.';

  @override
  String get teamUiHostKindDesktop => 'Computadora de escritorio';

  @override
  String get teamUiHostKindDisclaimerLaptop =>
      'El reposo y el cierre de la tapa pausan el equipo; las ejecuciones se reanudan al despertar';

  @override
  String get teamUiHostKindDisclaimerWsl =>
      'El reposo y el cierre de la tapa pausan el equipo; las ejecuciones se reanudan al despertar. WSL también se detiene al cerrar su última terminal.';

  @override
  String get teamUiHostKindHint =>
      'Solo cambia el recordatorio que se muestra con el equipo.';

  @override
  String get teamUiHostKindLabel => 'Clase de computadora';

  @override
  String get teamUiHostKindLaptop => 'Portátil';

  @override
  String get teamUiHostKindWsl => 'Windows (WSL)';

  @override
  String get teamUiReceiptSent => 'Enviado · esperando que el host confirme';

  @override
  String get teamUiReceiptAnswered => 'Respondido';

  @override
  String get teamUiReceiptUnconfirmed =>
      'Enviado, sin confirmación — revisa en el host antes de reenviarlo';

  @override
  String get teamUiGateAnswerSend => 'Enviar';

  @override
  String get teamUiGateAnswerApprove => 'Aprobar';

  @override
  String get teamUiGateAnswerDeny => 'Denegar';

  @override
  String get teamUiGateAnswerMarkDone => 'Marcar como hecho';

  @override
  String get teamUiGateAnswerHint => 'Teclea tu respuesta';

  @override
  String teamUiGateAnswerRunRetry(String work, String agent) {
    return 'Enviar $work de nuevo a $agent';
  }

  @override
  String get teamUiGateAnswerRunLogs => 'Observar al agente';

  @override
  String get teamUiGateAnswerRunCancel => 'Detener el trabajo';

  @override
  String teamUiGateAnswerRejected(String message) {
    return 'No aceptado: $message';
  }

  @override
  String get teamUiGateAnswerRejectedNoMessage =>
      'El host no aceptó esta respuesta.';

  @override
  String get teamUiGateAnswerConfirmApproveTitle =>
      '¿Aprobar esta acción destructiva?';

  @override
  String get teamUiGateAnswerConfirmApproveBody =>
      'El host la marca como destructiva. No puede deshacerse desde el teléfono.';

  @override
  String get teamUiGateAnswerConfirmCancelRunTitle => '¿Detener este trabajo?';

  @override
  String get teamUiGateAnswerConfirmCancelRunBody =>
      'La ejecución se detiene y su trabajo abierto se queda como está.';

  @override
  String get teamUiControlMessage => 'Mensaje';

  @override
  String get teamUiControlNudge => 'Empujón';

  @override
  String get teamUiControlPause => 'Pausa';

  @override
  String get teamUiControlResume => 'Reanudar';

  @override
  String get teamUiControlStop => 'Detener';

  @override
  String get teamUiControlRestart => 'Reiniciar';

  @override
  String get teamUiControlReassign => 'Reasignar el trabajo…';

  @override
  String get teamUiControlCreateWork => 'Tarea enviada a un agente';

  @override
  String teamUiControlStopConfirmTitle(String agent) {
    return '¿Detener $agent?';
  }

  @override
  String get teamUiControlStopConfirmBody =>
      'Su sesión termina ahora. Su trabajo se queda donde está; el host puede despertarlo de nuevo después.';

  @override
  String teamUiControlRestartConfirmTitle(String agent) {
    return '¿Reiniciar $agent?';
  }

  @override
  String get teamUiControlRestartConfirmBody =>
      'Su sesión se detiene y se encarga de nuevo. El agente pierde lo que tenía en contexto y retoma su trabajo desde el host.';

  @override
  String get teamUiControlKeep => 'Seguir adelante';

  @override
  String get teamUiControlReceiptSent => 'Enviado';

  @override
  String get teamUiControlReceiptConfirmed => 'Confirmado';

  @override
  String get teamUiControlReceiptUnconfirmed => 'Sin confirmación';

  @override
  String get teamUiControlReceiptRefused => 'Rechazado';

  @override
  String teamUiControlReceiptLine(String control, String state) {
    return '$control · $state';
  }

  @override
  String get teamUiControlCancelRun => 'Detener la ejecución';

  @override
  String get teamUiStartRunFab => 'Darle una tarea al equipo';

  @override
  String get teamUiStartRunTitle => 'Darle una tarea al equipo';

  @override
  String get teamUiStartRunObjectiveLabel => 'Objetivo';

  @override
  String get teamUiStartRunObjectiveHint =>
      '¿Qué debe lograr el equipo? Un resultado, con tus palabras.';

  @override
  String get teamUiStartRunObjectiveEmpty => 'Escribe primero un objetivo.';

  @override
  String get teamUiStartRunProjectLabel => 'Proyecto';

  @override
  String get teamUiStartRunProjectAny => 'Deja que el planificador elija';

  @override
  String get teamUiStartRunSupervisionLabel => 'Supervisión';

  @override
  String get teamUiStartRunSupervisionHigh => 'Alta';

  @override
  String get teamUiStartRunSupervisionHighHint =>
      'El equipo pregunta antes de cada decisión, antes de pruebas que cambian estado y antes de cualquier fusión.';

  @override
  String get teamUiStartRunSupervisionBalanced => 'Equilibrada';

  @override
  String get teamUiStartRunSupervisionBalancedHint =>
      'El equipo decide solo lo de la rutina y pregunta antes de fusiones, en los fallos y en las decisiones de diseño.';

  @override
  String get teamUiStartRunSupervisionAutonomous => 'Autónoma';

  @override
  String get teamUiStartRunSupervisionAutonomousHint =>
      'El equipo trabajó hasta terminar dentro de los límites del host y pregunta solo cuando no puede continuar.';

  @override
  String get teamUiStartRunPlannerLabel => 'Planificador';

  @override
  String get teamUiStartRunPlannerMayor => 'Mayor';

  @override
  String teamUiStartRunSend(String planner) {
    return 'Enviar al $planner';
  }

  @override
  String get teamUiStartRunHostGuide => 'Guía del host';

  @override
  String get teamUiStartRunWaking => 'Despertando al planificador…';

  @override
  String get teamUiStartRunDirectIntro =>
      'El planificador está apagado en este host. Dale una tarea directa al agente del proyecto.';

  @override
  String get teamUiStartRunDirectTitle => 'Tarea';

  @override
  String get teamUiStartRunDirectTitleHint =>
      'Una línea: ¿qué debe hacer el agente?';

  @override
  String get teamUiStartRunDirectTitleRequired => 'Escribe primero una tarea.';

  @override
  String get teamUiStartRunDirectDetails => 'Detalles (opcional)';

  @override
  String get teamUiStartRunDirectSend => 'Enviar a un agente';

  @override
  String get teamUiStartRunRefused => 'El host no aceptó esta tarea.';

  @override
  String get teamUiMergeTitleNotReady => 'Aún no listo para fusionar';

  @override
  String teamUiMergeRequest(String id) {
    return 'petición de fusión $id';
  }

  @override
  String get teamUiMergeLineWork => 'Elementos de trabajo';

  @override
  String get teamUiMergeLineTests => 'Pruebas';

  @override
  String get teamUiMergeLineBuild => 'Compilación';

  @override
  String get teamUiMergeLineReview => 'Revisión';

  @override
  String get teamUiMergeLineConflicts => 'No hay conflictos';

  @override
  String get teamUiMergeLineAcceptance => 'Criterios de aceptación';

  @override
  String teamUiMergeFiles(int files, int additions, int deletions) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files archivos · +$additions / −$deletions',
      one: '1 archivo · +$additions / −$deletions',
      zero: 'No hay cambios de archivos',
    );
    return '$_temp0';
  }

  @override
  String get teamUiMergeReviewChanges => 'Revisar los cambios';

  @override
  String get teamUiMergeApprove => 'Aprobar la petición';

  @override
  String teamUiMergeApprovedBy(String login) {
    return 'Aprobado por $login';
  }

  @override
  String get teamUiMergeMerge => 'Fusionar';

  @override
  String teamUiMergeConfirmAction(String branch) {
    return 'Fusionar en $branch';
  }

  @override
  String teamUiMergeDisabledReason(String line, String detail) {
    return 'Fusión apagada: $line — $detail';
  }

  @override
  String teamUiMergeBoundary(String text) {
    return 'Límite del host: $text';
  }

  @override
  String get teamUiMergeRefused =>
      'El host no lo aceptó, así que no se fusionó nada.';

  @override
  String teamUiMergeMerged(String branch, String commit) {
    return 'Fusionado en $branch · $commit';
  }

  @override
  String teamUiMergeAlready(String branch) {
    return 'Ya está en $branch';
  }

  @override
  String teamUiMergeUnavailable(String reason) {
    return 'Preparación de fusión no disponible: $reason';
  }

  @override
  String get teamUiMergeNoRoles =>
      'El host no tiene roles de fusión para esta ejecución';

  @override
  String get teamUiMergeLoading => 'Revisando la preparación de la fusión…';

  @override
  String get teamUiMergePending => 'corriendo en el host';

  @override
  String get teamUiMergeChangesTitle => 'Cambios';

  @override
  String get teamUiMergeChangesEmpty =>
      'El host no reportó cambios de archivos';

  @override
  String get teamUiMergeChangesWork => 'Elementos de trabajo';

  @override
  String get teamUiMergeSent => 'Enviado · esperando que el host confirme';

  @override
  String get teamUiMergeApproveConfirmed => 'Aprobación registrada';

  @override
  String teamUiPolicySupervision(String level) {
    return 'Supervisión · $level';
  }

  @override
  String get teamUiPolicyBoundariesLabel => 'Límites';

  @override
  String get teamUiPolicyBoundariesNone => 'El host no tiene límites fijados';

  @override
  String get teamUiPolicyFromHost => 'Fijado en el host · solo lectura aquí';

  @override
  String teamUiPolicyRig(String rig) {
    return 'para $rig';
  }

  @override
  String teamUiPolicySemantics(String level, String boundaries) {
    return 'Supervisión $level. Límites: $boundaries';
  }

  @override
  String get teamUiRunLabelRawTitle => 'Título del proveedor';

  @override
  String get termuxProcsAutoRefresh =>
      'Se actualiza cada 10 segundos mientras está abierto';

  @override
  String get termuxProcsStopping => 'Deteniendo…';

  @override
  String get termuxProcsCommand => 'Comando';

  @override
  String termuxProcsDurationSeconds(int seconds) {
    return '$seconds s';
  }

  @override
  String termuxProcsDurationMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String termuxProcsDurationHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String termuxProcsMemoryMb(int mb) {
    return '$mb MB';
  }

  @override
  String get teamUiPhoneChooseProjectTitle => 'Elige un proyecto';

  @override
  String get teamUiPhoneSuccessTitle => 'AI Team corre en este teléfono';

  @override
  String get phoneServerStart => 'Iniciar';

  @override
  String get phoneServerRestartFailed =>
      'El servidor no se reinició. Abre Gestionar la configuración para ver el porqué.';

  @override
  String get safetyStopSharingTitle => '¿Dejar de compartir esta conversación?';

  @override
  String get safetyStopSharingBody =>
      'El enlace deja de funcionar para cualquiera que lo tenga. La conversación en sí no cambia.';

  @override
  String get safetyStopSharingKeep => 'Seguir compartiendo';

  @override
  String get safetyStopLocalServerTitle => '¿Detener el servidor local?';

  @override
  String get safetyStopLocalServerBody =>
      'Se interrumpe lo que el agente esté ejecutando en este teléfono y esta app se desconecta de él. Tus proyectos y conversaciones se quedan en el teléfono; vuelve a iniciar el servidor para continuar.';

  @override
  String get safetyStopLocalServerKeep => 'Seguir ejecutando';

  @override
  String safetyMcpDisconnectTitle(String server) {
    return '¿Desconectar $server?';
  }

  @override
  String get safetyMcpDisconnectBody =>
      'Los agentes pierden sus herramientas hasta que lo vuelvas a conectar, y una llamada a una herramienta en curso puede fallar. Su configuración se conserva.';

  @override
  String get safetyMcpDisconnectKeep => 'Seguir conectado';

  @override
  String get settingsHubGroupNotifications => 'Notificaciones y segundo plano';

  @override
  String get settingsHubGroupUsage => 'Consumo';

  @override
  String get settingsHubGroupHelp => 'Ayuda';

  @override
  String get settingsHubThisServer => 'Este servidor';

  @override
  String get settingsHubAccounts => 'Cuentas';

  @override
  String get settingsHubModelAndMode => 'Modelo y modo';

  @override
  String get settingsHubVoice => 'Voz';

  @override
  String get settingsHubPrivacyRow => 'Privacidad y datos';

  @override
  String get settingsHubSearchServerAliases =>
      'servidor host url dirección contraseña perfil conexión salud estado versión actualizar servicio';

  @override
  String get settingsHubSearchSavedServersAliases =>
      'servidores host url contraseña cambiar agregar editar quitar perfil conexión perfiles';

  @override
  String get settingsHubSearchPhoneAliases =>
      'teléfono local en el dispositivo android instalar configuración almacenamiento servicios linux';

  @override
  String get settingsHubSearchAccountsAliases =>
      'cuenta codex iniciar sesión entrar salir';

  @override
  String get settingsHubSearchExternalAgentsAliases =>
      'a2a agentes externos remoto';

  @override
  String get settingsHubSearchTailscaleAliases =>
      'tailscale vpn red remoto privado';

  @override
  String get settingsHubSearchDisconnectAliases =>
      'desconectar abandonar servidor';

  @override
  String get settingsHubSearchModelModeAliases =>
      'modelo modo agente variante razonamiento predeterminado seleccionado sesión chat conversación';

  @override
  String get settingsHubSearchShellAliases => 'shell terminal bash zsh comando';

  @override
  String get settingsHubSearchPermissionsAliases =>
      'permisos aprobaciones siempre permitir permitido revocar';

  @override
  String get settingsHubSearchTranscriptAliases =>
      'transcripción presentación razonamiento marcas de tiempo consumo';

  @override
  String get settingsHubSearchVoiceAliases =>
      'voz habla micrófono dictado modelo';

  @override
  String get settingsHubSearchNotificationsAliases =>
      'notificaciones y fondo mantener funcionando lo que corre solo automatización acciones siempre permitidas alertas horas tranquilas revisión de fondo check-in wi-fi wifi monitorear servidores guardados ejecuciones terminadas aprobaciones preguntas umbrales de cuota';

  @override
  String get settingsHubSearchAppearanceAliases =>
      'apariencia tema oscuro claro idioma árabe inglés colores';

  @override
  String get settingsHubSearchModelsAliases =>
      'modelos agentes proveedor IA razonamiento favoritos recientes';

  @override
  String get settingsHubSearchProvidersAliases =>
      'proveedor clave api claves autenticación conectar';

  @override
  String get settingsHubSearchMcpAliases =>
      'mcp integraciones servidores herramientas';

  @override
  String get settingsHubSearchCommandsAliases =>
      'comandos herramientas skills referencias barra capacidades';

  @override
  String get settingsHubSearchPluginsAliases =>
      'plugins plugin instalado fuente estado AI Team Gas City';

  @override
  String get settingsHubSearchUsageAliases =>
      'uso costo tokens presupuesto cuota límite gastado restante umbral monitoreo de cuota proveedor planes suscripciones gastos alertas';

  @override
  String get settingsHubSearchPrivacyAliases =>
      'privacidad borradores fila mensajes en fila estado leído almacenamiento vacíar eliminar';

  @override
  String get settingsHubSearchGuideAliases =>
      'ayuda guía conectar tutorial empezar';

  @override
  String get settingsHubSearchBugAliases =>
      'error retroalimentación problema soporte reportar fallo diagnóstico errores registro github';

  @override
  String get settingsHubSearchDiagnosticsAliases =>
      'diagnóstico depuración errores registro';

  @override
  String get settingsHubSearchAboutAliases =>
      'acerca de versión licencias código abierto avisos privacidad datos';

  @override
  String get pluginsSectionOnServer => 'Plugins en este servidor';

  @override
  String get notifySectionWhat => 'Qué me notifica';

  @override
  String get notifyFinishedRuns => 'Trabajo terminado';

  @override
  String get notifyFinishedRunsDetail =>
      'Varias llegan en una sola notificación.';

  @override
  String get notifyRequests => 'Preguntas para ti';

  @override
  String get notifyRequestsDetail => 'Respóndelas desde la notificación.';

  @override
  String get notifyQuotaAlerts => 'Alertas de uso';

  @override
  String get notifyQuotaAlertsDetail => 'Se define en Uso › Alertas.';

  @override
  String get notifyBlockedTitle =>
      'Las notificaciones están desactivadas para esta app';

  @override
  String get notifyBlockedMessage =>
      'Android no deja que OpenCode te avise, así que las preguntas y las conversaciones terminadas no pueden llegarte.';

  @override
  String get notifySendTest => 'Enviar una notificación de prueba';

  @override
  String get notifySendTestDetail => 'Comprueba si Android las deja pasar.';

  @override
  String get notifyQuietDetail => 'En silencio en las horas que elijas.';

  @override
  String get notifySectionBackground => 'Segundo plano';

  @override
  String get notifySectionServers => 'Servidores guardados';

  @override
  String get notifyWifiOnly => 'Revisar servidores solo con Wi-Fi';

  @override
  String get usageSectionSpent => 'Gastado';

  @override
  String get usageSectionRemaining => 'Restante';

  @override
  String get serverSwitcherManage => 'Administrar servidores';

  @override
  String get serverSwitcherOpen => 'Cambiar de servidor';

  @override
  String get discoverSearchGoTo => 'Ir a';

  @override
  String discoverSearchIn(String parent) {
    return 'En $parent';
  }

  @override
  String get discoverProjectAliases =>
      'herramientas del proyecto código carpeta archivos cambios terminal salud worktrees';

  @override
  String get discoverFilesAliases =>
      'archivos explorar trabajar el árbol vista previa código abrir archivo';

  @override
  String get discoverChangesAliases =>
      'cambios revisar cambios diff sin comprometer git ediciones árbol de trabajo';

  @override
  String get discoverTerminalAliases =>
      'terminal shell consola línea de comandos pty';

  @override
  String get discoverHealthAliases =>
      'salud del proyecto rama archivos cambiados servicios de lenguaje formateadores lsp';

  @override
  String get discoverWorktreesAliases =>
      'árboles de trabajo ramas aisladas git rama';

  @override
  String get discoverSearchFilesAliases =>
      'buscar archivos hallar nombre de archivo';

  @override
  String get discoverAllConversationsAliases =>
      'todas las conversaciones sesiones chats historial cada proyecto búsqueda';

  @override
  String get discoverTeamAliases =>
      'ai team agentes ejecuciones requiere tu atención orquestación';

  @override
  String get discoverNotifyServersTitle =>
      'Notificaciones de servidores guardados';

  @override
  String get discoverNotifyWhatAliases =>
      'ejecuciones terminadas aprobaciones preguntas revisiones alertas de cuota qué notifica';

  @override
  String get discoverNotifyQuietAliases =>
      'horas tranquilas no molestar noche silencio silenciar horario';

  @override
  String get discoverNotifyBackgroundAliases =>
      'fondo conexión mantenerse conectado seguir vivo servicio';

  @override
  String get discoverNotifyServersAliases =>
      'monitorear servidores guardados atención wi-fi wifi revisar en el fondo';

  @override
  String get discoverAppearanceModeAliases =>
      'claro oscuro modo sistema apariencia noche';

  @override
  String get discoverLanguageAliases =>
      'idioma árabe inglés locale traducción rtl';

  @override
  String get discoverThemeAliases => 'tema colores paleta paquete acento';

  @override
  String get discoverSpentAliases =>
      'gastado costo tokens uso estadísticas dinero';

  @override
  String get discoverRemainingAliases =>
      'planes suscripciones restante cuota límite plan del proveedor lo que queda reajuste codex claude glm minimax';

  @override
  String get discoverBudgetAliases =>
      'presupuesto presupuestos usd presupuesto de tokens límite de gastos';

  @override
  String get discoverQuotaMonitorAliases =>
      'alertas de uso monitoreo de cuota umbral alertar advertir escasas horas tranquilas wi-fi';

  @override
  String get discoverCommandsAliases =>
      'comandos comandos del servidor slash ejecutar';

  @override
  String get discoverToolsAliases =>
      'herramientas herramientas y capacidades inventario herramientas del modelo';

  @override
  String get discoverSkillsAliases =>
      'skills instrucciones de skill guía de jugadas';

  @override
  String get discoverReferencesAliases =>
      'referencias referencia docs fuentes contexto';

  @override
  String get discoverMonitorAliases =>
      'atención del servidor guardado monitorear otros servidores esperando la atención del servidor';

  @override
  String get discoverConnectionHelpAliases =>
      'agregar servidor computadora conectar emparejar código de emparejamiento ayuda de conexión no puedo conectar solución de problemas red denegado tiempo agotado';

  @override
  String gestureEquivFileRowActions(String name) {
    return 'Acciones para $name';
  }

  @override
  String get gestureEquivShortcutFindMatch =>
      'Coincidencia siguiente / anterior mientras buscas en una conversación';

  @override
  String get gestureEquivShortcutPromptHistory =>
      'Mensaje anterior / posterior, con el cursor al principio o al final del inbox de mensajes';

  @override
  String get emptyTeachChangesTitle => 'Aún no hay cambios';

  @override
  String get emptyTeachChangesMessage =>
      'Las ediciones que el agente hace aparecen aquí para revisar.';

  @override
  String get emptyTeachWorktreesMessage =>
      'Un árbol de trabajo es una copia aparte de este proyecto en su propia rama, para que el trabajo en paralelo no se mezcle. Los worktrees de este proyecto aparecen aquí.';

  @override
  String get emptyTeachAllowedMessage =>
      'Cuando eliges Permitir siempre en una aprobación de este proyecto, queda listada aquí para que puedas revocarla.';

  @override
  String get emptyTeachTeamRunsMessage =>
      'Dile qué necesitas, y el equipo lo reparte en pasos y muestra aquí su progreso.';

  @override
  String get emptyTeachSkillsMessage =>
      'Las skills son instrucciones reutilizables que el agente puede seguir. Las skills de este proyecto y de este servidor aparecen aquí.';

  @override
  String get emptyTeachToolsMessage =>
      'Las herramientas que el agente puede llamar con este modelo aparecen aquí. Este modelo no tiene ninguna.';

  @override
  String get capabilityScreenTitle => 'Disponible en este servidor';

  @override
  String get capabilityScreenAliases =>
      'disponible admitido no disponible función faltante oculto por qué no puede capacidades admite el servidor shell';

  @override
  String capabilityScreenIntro(String server) {
    return '$server decide qué aparece en esta app. Lo que no puede hacer se queda fuera de los menús y las pestañas, en vez de mostrarlo en gris.';
  }

  @override
  String get capabilityAllAvailable =>
      'Este servidor soporta todo lo que la app ofrece.';

  @override
  String get capabilityFiles => 'Archivos';

  @override
  String get capabilityFilesDetail =>
      'Explora, busca y previsualiza los archivos del proyecto';

  @override
  String get capabilityChanges => 'Cambios';

  @override
  String get capabilityChangesDetail => 'Revisa lo que el agente editó';

  @override
  String get capabilityTerminal => 'Terminal';

  @override
  String get capabilityTerminalDetail => 'Ejecuta comandos en el proyecto';

  @override
  String get capabilityShell => 'Shell por defecto';

  @override
  String get capabilityShellDetail =>
      'Elige el shell que usan los comandos y las terminales';

  @override
  String get capabilityAttachments => 'Adjuntos';

  @override
  String get capabilityAttachmentsDetail =>
      'Envía archivos y fotos con un mensaje';

  @override
  String get capabilitySubagents => 'Delegar a un sub-agente';

  @override
  String get capabilitySubagentsDetail =>
      'Menciona a un agente con @ en un mensaje';

  @override
  String get capabilityCompact => 'Compactar';

  @override
  String get capabilityCompactDetail =>
      'Resume una conversación larga para liberar contexto';

  @override
  String get capabilityShare => 'Compartir';

  @override
  String get capabilityShareDetail => 'Publica un enlace a una conversación';

  @override
  String get capabilityFork => 'Bifurcar';

  @override
  String get capabilityForkDetail =>
      'Bifurca una conversación desde un mensaje anterior';

  @override
  String get capabilityRevert => 'Revertir';

  @override
  String get capabilityRevertDetail =>
      'Deshacer un mensaje y las ediciones que hizo';

  @override
  String get capabilityArchive => 'Archivar';

  @override
  String get capabilityArchiveDetail =>
      'Guarda las conversaciones terminadas sin borrarlas';

  @override
  String get capabilityTodos => 'Tareas';

  @override
  String get capabilityTodosDetail =>
      'Mira la lista de tareas del agente para una conversación';

  @override
  String get capabilityNotes => 'Nota para el agente';

  @override
  String get capabilityNotesDetail =>
      'Conserva instrucciones permanentes con una conversación';

  @override
  String get capabilityImportExport => 'Importar y exportar';

  @override
  String get capabilityImportExportDetail =>
      'Mueve una conversación entre servidores como archivo';

  @override
  String get capabilitySearchAll => 'Todas las conversaciones';

  @override
  String get capabilitySearchAllDetail =>
      'Busca conversaciones en todos los proyectos';

  @override
  String get capabilityAlwaysAllow => 'Acciones siempre permitidas';

  @override
  String get capabilityAlwaysAllowDetail =>
      'Recuerda una aprobación para que no se pida otra vez';

  @override
  String get capabilityModels => 'Modelos y proveedores';

  @override
  String get capabilityModelsDetail =>
      'Explora modelos e inicia sesión en proveedores desde la app';

  @override
  String get capabilitySkills => 'Skills y comandos';

  @override
  String get capabilitySkillsDetail =>
      'Listar las skills, comandos y referencias del servidor';

  @override
  String get capabilityMcp => 'MCP';

  @override
  String get capabilityMcpDetail => 'Ver y conectar servidores MCP';

  @override
  String get capabilityPlugins => 'Plugins';

  @override
  String get capabilityPluginsDetail =>
      'Ver los plugins instalados en el servidor';

  @override
  String get capabilityCloud => 'Entornos en la nube';

  @override
  String get capabilityCloudDetail =>
      'Hacer correr un proyecto en un entorno gestionado';

  @override
  String get capabilityProjects => 'Proyectos';

  @override
  String get capabilityProjectsDetail =>
      'Cambiar de proyecto y revisar la salud de un proyecto';

  @override
  String get capabilityWorktrees => 'Árboles de trabajo';

  @override
  String get capabilityWorktreesDetail =>
      'Dar a una tarea su propia rama aislada';

  @override
  String get capabilityUsage => 'Consumo';

  @override
  String get capabilityUsageDetail =>
      'Ver lo que las conversaciones han costado';

  @override
  String get capabilityOfflineQueue => 'Enviar después';

  @override
  String get capabilityOfflineQueueDetail =>
      'En fila un mensaje sin conexión y envíalo al momento de reconectar';

  @override
  String get capabilityContinueOnComputer => 'Continuar en la computadora';

  @override
  String get capabilityContinueOnComputerDetail =>
      'Obtén un comando que reabre la conversación en tu escritorio';

  @override
  String get capabilityServerUpdates => 'Actualizaciones del servidor';

  @override
  String get capabilityServerUpdatesDetail =>
      'Actualizar el servidor desde la app';

  @override
  String get capabilityBackgroundNotifications => 'Notificaciones en el fondo';

  @override
  String get capabilityBackgroundNotificationsDetail =>
      'Se te avisa cuando el trabajo termina o requiere tu atención con la app cerrada';

  @override
  String get capabilityOnThisPhone => 'En este teléfono';

  @override
  String get capabilityOnThisPhoneDetail =>
      'Hace correr el servidor del agente en este dispositivo';

  @override
  String get capabilityVoice => 'Voz';

  @override
  String get capabilityVoiceDetail =>
      'Dicta mensajes con modelos de voz en el dispositivo';

  @override
  String get discoverShowTipsAgain => 'Mostrar los recuerdos otra vez';

  @override
  String get discoverShowTipsSubtitle =>
      'Los recuerdos de una sola vez aparecerán de nuevo a su momento';

  @override
  String get discoverShowTipsAliases =>
      'recuerdos pistas ayudas ayuda reiniciar mostrar otra vez tutorial';

  @override
  String get discoverShowTipsDone => 'Los recuerdos se volverán a mostrar.';

  @override
  String nudgeApprovals(String action) {
    return 'Se preguntó por “$action” 3 veces: esta conversación puede aprobar peticiones por ti.';
  }

  @override
  String get nudgeReviewChanges =>
      'OpenCode cambió archivos. Revísalos antes de seguir.';

  @override
  String get nudgeLeave =>
      'Puedes irte: este teléfono te avisa cuando la ejecución termina.';

  @override
  String nudgeCompact(String percent) {
    return 'El contexto está al $percent% lleno: compáctalo para seguir.';
  }

  @override
  String get nudgeDismiss => 'Ocultar el recuerdo';

  @override
  String get firstRunWhereQuestion => '¿Dónde debería trabajar tu agente?';

  @override
  String get firstRunOnComputer => 'En mi computadora';

  @override
  String get firstRunJustShowMe => 'Solo quiero verlo';

  @override
  String get firstRunAgentOpenCode => 'OpenCode';

  @override
  String get firstRunAgentCodex => 'Codex';

  @override
  String get firstRunRunOnComputer => 'En tu computadora, ejecuta:';

  @override
  String get firstRunPairingNextScan =>
      'Muestra un código. Escanéalo, o cópialo y pégalo aquí.';

  @override
  String get firstRunPairingNextPaste => 'Luego pega el código que imprime.';

  @override
  String get firstRunNotSameNetwork => '¿No estás en la misma red?';

  @override
  String get firstRunShowCommands => 'Mostrar los comandos';

  @override
  String get firstRunCommandsPaseoNetwork =>
      'Para acceder desde este teléfono por tu red privada, escucha en esa dirección y define una contraseña:';

  @override
  String get firstRunCommandsCodexToken =>
      'Crea el token de conexión antes de iniciarlo:';

  @override
  String get firstRunCommandsCodexUsb =>
      'Un teléfono conectado por cable USB accede con:';

  @override
  String get firstRunNotifyTitle => '¿Avisarte cuando el agente te necesite?';

  @override
  String get firstRunNotifyBody =>
      'Sal de la app mientras el agente trabaja. Recibirás una notificación cuando necesite tu respuesta. Android muestra una pequeña notificación permanente mientras está conectado.';

  @override
  String get firstRunNotifyAccept => 'Avisarme';

  @override
  String get firstRunNotifyDecline => 'Ahora no';

  @override
  String get localAgentTitle => 'Claude Code en este teléfono';

  @override
  String get firstRunAgentsSideBySide =>
      'Se ejecutan en paralelo en la misma computadora. Empieza con uno y agrega los demás cuando quieras desde el nombre del servidor, arriba.';

  @override
  String get firstRunPaseoTitle => 'Agentes a través de Paseo';

  @override
  String get agentErrorConnectionDropped =>
      'Se perdió la conexión con el modelo.';

  @override
  String get agentErrorConnectionDroppedHint =>
      'Revisa el internet del dispositivo donde se ejecuta OpenCode. Si la respuesta se detuvo, revisa la conversación antes de enviar de nuevo.';

  @override
  String get agentErrorTimedOut => 'El modelo tardó demasiado en responder.';

  @override
  String get agentErrorTimedOutHint =>
      'Si la respuesta se detuvo, prueba con una solicitud más pequeña u otro modelo.';

  @override
  String get agentErrorProviderUnreachable =>
      'El servidor no pudo contactar al proveedor del modelo.';

  @override
  String get agentErrorProviderUnreachableHint =>
      'Revisa la conexión a internet de la computadora donde se ejecuta OpenCode y vuelve a intentarlo.';

  @override
  String get agentErrorRequestTooLarge =>
      'Esta solicitud era demasiado grande para enviarla al modelo.';

  @override
  String get agentErrorRequestTooLargeHint =>
      'Envía menos adjuntos o más pequeños, o compacta la conversación, y vuelve a intentarlo.';

  @override
  String get agentErrorRateLimited =>
      'El proveedor del modelo limita la velocidad a la que puedes enviar.';

  @override
  String get agentErrorRateLimitedHint =>
      'Espera un poco. Si la respuesta se detuvo, inténtalo de nuevo o elige otro modelo.';

  @override
  String get agentErrorProviderBusy =>
      'El proveedor del modelo está sobrecargado ahora mismo.';

  @override
  String get agentErrorProviderBusyHint =>
      'Si la respuesta se detuvo, inténtalo más tarde o elige otro modelo.';

  @override
  String get agentErrorOutOfCredit =>
      'La cuenta del proveedor se quedó sin crédito o cuota.';

  @override
  String get agentErrorOutOfCreditHint =>
      'Recarga la cuenta o cambia a otro proveedor o modelo.';

  @override
  String get agentErrorUsageLimit =>
      'El límite de uso de este modelo se alcanzó por ahora.';

  @override
  String get agentErrorUsageLimitHint =>
      'Elige otro modelo para seguir, o continúa después del reajuste del límite.';

  @override
  String get agentErrorServerDiskFull =>
      'La computadora donde se ejecuta OpenCode se quedó sin espacio en disco.';

  @override
  String get agentErrorServerDiskFullHint =>
      'Libera espacio allí y vuelve a intentarlo.';

  @override
  String get agentErrorRecovered => 'El agente continuó después de esto.';

  @override
  String chatUiRetryingSoon(String attempt) {
    return 'Reintentando$attempt…';
  }

  @override
  String chatUiRetryingCountdown(String attempt, String time) {
    return 'Reintentando$attempt en $time';
  }

  @override
  String get chatUiCompactAgain => 'Compactar de nuevo';

  @override
  String get chatUiCompactionFailedHint =>
      'La conversación sigue siendo demasiado larga para el modelo, así que el próximo turno lo intentará de nuevo.';

  @override
  String get chatStripContextPending => 'Contexto pendiente';

  @override
  String get chatStripAutoApprove => 'Aprobación automática';

  @override
  String get chatChipAutoApproveOn => 'Aprobación automática activada';

  @override
  String get chatStripAutoApprovePaused => 'Aprobación automática en pausa';

  @override
  String get approvalModeMenuLabel => 'Modo de aprobación';

  @override
  String get approvalModeAskTitle => 'Preguntar primero';

  @override
  String get approvalModeAutoTitle =>
      'Aprobar automáticamente esta conversación';

  @override
  String get approvalModeAutoDetail => 'Se permiten una vez, según llegan.';

  @override
  String get approvalModeEverythingTitle => 'Aprobar todo';

  @override
  String get approvalModeEverythingDetail =>
      'Todas las conversaciones de este servidor.';

  @override
  String approvalModeEverythingAgentDetail(String agent) {
    return 'Todas las conversaciones de $agent en este teléfono.';
  }

  @override
  String get approvalModeSettings => 'Ajustes de aprobación…';

  @override
  String get approvalModeConfirmEverythingTitle => '¿Aprobar todo?';

  @override
  String get approvalModeConfirmEverythingAction => 'Aprobar todo';

  @override
  String get approvalModeNowAsk => 'Esta conversación pregunta primero.';

  @override
  String get approvalModeNowAuto =>
      'Esta conversación aprueba automáticamente.';

  @override
  String get approvalModeNowEverything => 'Aprobando todo en este servidor.';

  @override
  String get approvalModeChange => 'Cambiar el modo de aprobación';

  @override
  String get chatStripBackground => 'Segundo plano';

  @override
  String chatAttachmentOfficeHeader(String name) {
    return 'Contenido de $name, leído en el teléfono: solo valores y texto, sin formato, gráficos ni fórmulas.';
  }

  @override
  String get chatAttachmentOfficeTruncated =>
      'El archivo era más grande de lo que cabe en un mensaje, así que se corta más abajo.';

  @override
  String chatAttachmentOfficeUnreadable(String name) {
    return 'No se pudo leer $name. Puede estar protegido con contraseña, dañado o en el formato antiguo de Excel o Word; guárdalo como .xlsx, .docx o CSV e inténtalo de nuevo.';
  }

  @override
  String chatAttachmentOfficeEmpty(String name) {
    return '$name no tiene nada que adjuntar.';
  }

  @override
  String chatAttachmentDocumentAttached(String name) {
    return '$name adjuntado como texto.';
  }

  @override
  String chatAttachmentSheetAttached(String name, int sheets, int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      sheets,
      locale: localeName,
      other: '$sheets hojas',
      one: '1 hoja',
    );
    String _temp1 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: '$rows filas',
      one: '1 fila',
    );
    return '$name adjuntado como texto · $_temp0, $_temp1';
  }

  @override
  String get modelNewBadge => 'Nuevo';

  @override
  String get modelEffortNone => 'Sin razonamiento';

  @override
  String get modelEffortMinimal => 'Mínimo';

  @override
  String get modelEffortLow => 'Bajo';

  @override
  String get modelEffortMedium => 'Medio';

  @override
  String get modelEffortHigh => 'Alto';

  @override
  String get modelEffortExtraHigh => 'Muy alto';

  @override
  String get modelEffortMax => 'Máximo';

  @override
  String get setupStoppedByRestart =>
      'El teléfono se reinició, así que el servidor local se detuvo. Vuelve a iniciarlo cuando lo necesites.';

  @override
  String get phoneServerStartAndConnect => 'Iniciar y conectar';

  @override
  String otherServerWorking(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count en curso',
      one: '1 en curso',
    );
    return '$_temp0';
  }

  @override
  String chatUiAskAgent(String agent) {
    return 'Pregunta a $agent…';
  }

  @override
  String terminalShowEarlier(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar $countString líneas anteriores',
      one: 'Mostrar 1 línea anterior',
    );
    return '$_temp0';
  }

  @override
  String terminalOpenFull(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'Abrir las $countString líneas';
  }

  @override
  String get setupProgressViewOverallLabel => 'Progreso de la configuración';

  @override
  String get setupProgressViewGettingStarted => 'Empezando…';

  @override
  String setupProgressViewMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '~$minutes min restantes',
      one: '~1 min restante',
    );
    return '$_temp0';
  }

  @override
  String get setupProgressViewUnderMinute => 'Menos de un minuto';

  @override
  String get setupProgressViewDone => 'Todo listo';

  @override
  String get setupProgressViewFailedTitle => 'La configuración no terminó';

  @override
  String get setupProgressViewInterrupted =>
      'La configuración se interrumpió. Lo ya terminado se conserva.';

  @override
  String get setupProgressViewCancelled =>
      'Configuración detenida. Lo ya terminado sigue instalado.';

  @override
  String setupProgressViewBytes(String done, String total) {
    return '$done de $total';
  }

  @override
  String setupProgressViewPercent(int percent) {
    return '$percent%';
  }

  @override
  String setupProgressViewStageMeasured(String stage, String measured) {
    return '$stage · $measured';
  }

  @override
  String get setupProgressViewChecking => 'Comprobando';

  @override
  String get setupProgressViewStarting => 'Iniciando';

  @override
  String setupProgressViewFailedDuring(String stage) {
    return 'Se detuvo durante: $stage. Lo que falló está en Detalles.';
  }

  @override
  String get setupProgressViewFailedUnknown =>
      'La configuración se detuvo antes de terminar. Lo que falló está en Detalles.';

  @override
  String get setupProgressViewNoInternet =>
      'Sin conexión a internet — Continúa cuando vuelvas a estar en línea';

  @override
  String get setupProgressViewContinue => 'Continuar la configuración';

  @override
  String get setupProgressViewCancel => 'Detener la configuración';

  @override
  String get setupProgressViewNoLog => 'Aún no hay nada registrado.';

  @override
  String get phoneSetupProgressTitle =>
      'Configurando OpenCode en este teléfono';

  @override
  String get builtinServerChooseRuntime => 'Cuál OpenCode';

  @override
  String get builtinServerStopped => 'No se está ejecutando.';

  @override
  String builtinServerStartFailed(String reason) {
    return 'OpenCode no respondió: $reason. Abre el registro para ver por qué.';
  }

  @override
  String get builtinServerExited => 'el servidor se detuvo';

  @override
  String builtinServerTimedOut(int seconds) {
    return 'sin respuesta en $seconds segundos';
  }

  @override
  String builtinServerConnectFailed(String reason) {
    return 'No se pudo conectar: $reason';
  }

  @override
  String builtinServerProfileName(String runtime) {
    return 'Este teléfono, integrado ($runtime)';
  }

  @override
  String get phoneSetupProfileName => 'Este teléfono';

  @override
  String get phoneSetupLinuxTitle => 'Base de Linux';

  @override
  String get phoneSetupLinuxWhy => 'Todo lo demás se ejecuta dentro de ella.';

  @override
  String get phoneSetupEssentialsTitle => 'Git, SSH y certificados';

  @override
  String get phoneSetupEssentialsShort => 'Git y SSH';

  @override
  String get phoneSetupEssentialsWhy =>
      'Los agentes usan Git y SSH para trabajar en tus proyectos.';

  @override
  String get phoneSetupNodeWhy => 'OpenCode se ejecuta sobre Node.js.';

  @override
  String get phoneSetupOpenCodeWhy => 'El agente de programación en sí.';

  @override
  String get phoneSetupStartTitle => 'Iniciar OpenCode';

  @override
  String get phoneSetupStartWhy =>
      'La configuración termina con OpenCode en marcha.';

  @override
  String get phoneSetupStageDownloadingLinux => 'Descargando la base de Linux';

  @override
  String get phoneSetupStageUnpackingLinux =>
      'Descomprimiendo la base de Linux';

  @override
  String get phoneSetupStageStarting => 'Iniciando OpenCode';

  @override
  String get phoneSetupNotificationChannel => 'Configuración del teléfono';

  @override
  String get phoneSetupNotificationTitle =>
      'Configurando OpenCode en este teléfono';

  @override
  String phoneSetupNotificationProgress(String percent) {
    return '$percent% completado';
  }

  @override
  String get phoneSetupNotificationDone =>
      'OpenCode está listo en este teléfono';

  @override
  String get phoneSetupNotificationStopped =>
      'La configuración se detuvo. Abre la app para continuar.';

  @override
  String get phoneSetupErrorNoInternet =>
      'Sin conexión a internet. Continúa cuando vuelvas a estar en línea.';

  @override
  String phoneSetupErrorOffline(String name) {
    return 'No se pudo descargar $name: sin conexión a internet';
  }

  @override
  String phoneSetupErrorInstall(String name) {
    return 'No se pudo instalar $name';
  }

  @override
  String phoneSetupErrorChecksum(String name) {
    return 'La descarga de $name se dañó. Continúa para descargarla de nuevo.';
  }

  @override
  String get phoneSetupErrorOpenCodeNoProgram =>
      'OpenCode se descargó, pero su programa no estaba en la descarga. Continúa para descargarlo de nuevo.';

  @override
  String get phoneSetupErrorOpenCodeWontRun =>
      'OpenCode se descargó, pero su programa no se ejecuta en este teléfono. Los detalles muestran lo que dijo.';

  @override
  String get phoneSetupErrorOpenCodeNoStart =>
      'OpenCode se instaló, pero no se inició. Continúa para intentarlo de nuevo; los detalles muestran lo que dijo.';

  @override
  String phoneSetupErrorNoSpace(String name) {
    return 'No hay espacio libre suficiente para instalar $name. Libera almacenamiento y continúa la configuración.';
  }

  @override
  String get phoneSetupErrorCannotStart =>
      'OpenCode está instalado pero no responde. Continúa la configuración para intentarlo de nuevo. Los detalles muestran qué lo detuvo.';

  @override
  String get builtinServerLogTitle => 'Registro del servidor';

  @override
  String get phoneSetupReadyTitle => 'OpenCode está listo';

  @override
  String get phoneSetupReadyNameTitle => 'Ponle nombre a tu primer proyecto';

  @override
  String get phoneSetupReadyNameLabel => 'Nombre del proyecto';

  @override
  String get phoneSetupReadyNameHelp => 'Letras, números, - _ .';

  @override
  String get phoneSetupReadyCreating => 'Creando el proyecto';

  @override
  String get phoneSetupReadyCreateOpen => 'Crear y abrir';

  @override
  String get phoneSetupReadyOpenFolderInstead =>
      'Abrir una carpeta en su lugar';

  @override
  String get phoneSetupReadyNameEmpty => 'Escribe un nombre.';

  @override
  String get phoneSetupReadyNameOneFolder => 'Usa un solo nombre, sin barras.';

  @override
  String get phoneSetupReadyNameInvalid =>
      'Usa letras, números, - _ o . y empieza por una letra o un número (hasta 64).';

  @override
  String phoneSetupReadyCreateFailed(String reason) {
    return 'No se pudo crear el proyecto: $reason';
  }

  @override
  String phoneSetupReadyOpenFailed(String reason) {
    return 'No se pudo abrir el proyecto: $reason';
  }

  @override
  String get phoneServerCardTitle => 'Ubuntu en la app';

  @override
  String get phoneServerCardRunning => 'En ejecución';

  @override
  String get phoneServerCardStopped => 'Detenido';

  @override
  String get phoneServerCardStarting => 'Iniciando';

  @override
  String get phoneServerCardStopping => 'Deteniendo';

  @override
  String get phoneServerCardRemoving => 'Quitando';

  @override
  String get phoneServerCardChecking => 'Comprobando';

  @override
  String get phoneServerCardNotSetUp => 'Sin configurar';

  @override
  String get phoneServerCardSettingUp => 'Configurando';

  @override
  String phoneServerCardVersion(String version) {
    return 'OpenCode $version';
  }

  @override
  String get phoneServerCardSetUp => 'Configurar';

  @override
  String get phoneServerCardShowProgress => 'Ver progreso';

  @override
  String get phoneServerCardContinueSetup => 'Continuar la configuración';

  @override
  String get phoneServerCardLogTitle => 'Registro';

  @override
  String get phoneServerCardLogEmpty => 'Aún no hay nada en el registro.';

  @override
  String get phoneServerCardMore => 'Más';

  @override
  String get phoneServerCardAddTools =>
      'Agregar herramientas (Python, AI Team…)';

  @override
  String get phoneServerCardUpdate => 'Actualizar OpenCode';

  @override
  String get phoneServerCardRemove => 'Quitar de este teléfono…';

  @override
  String get phoneServerCardRemoveTitle => '¿Quitar OpenCode de este teléfono?';

  @override
  String get serverEditorMoreOptions => 'Más opciones';

  @override
  String get inAppServerStoppedTitle =>
      'OpenCode dentro de la app está detenido';

  @override
  String get inAppServerStoppedBody =>
      'Se detiene cuando la app se cierra un tiempo o se actualiza. Tus conversaciones se conservan; inícialo de nuevo para continuar.';

  @override
  String get inAppServerNotRespondingTitle =>
      'OpenCode dentro de la app no responde';

  @override
  String get inAppServerNotRespondingBody =>
      'Iniciarlo de nuevo suele solucionarlo. Tus conversaciones se conservan.';

  @override
  String get inAppServerStartFailedTitle =>
      'OpenCode dentro de la app no se inició';

  @override
  String get inAppServerStartFailedBody =>
      'Abre su configuración para ver el registro del servidor, o intenta iniciarlo de nuevo.';

  @override
  String get inAppServerStarting => 'Iniciando OpenCode dentro de la app…';

  @override
  String get inAppServerStartingBody => 'Tarda unos segundos.';

  @override
  String get inAppServerOpenSetup => 'Abrir configuración';

  @override
  String get projectFolderNewProject => 'Proyecto nuevo';

  @override
  String get projectFolderProjectNameLabel => 'Nombre del proyecto';

  @override
  String get projectFolderEnterPath => 'Introducir una ruta';

  @override
  String get projectFolderMissing => 'Esa carpeta no existe todavía.';

  @override
  String get projectFolderCreateIt => 'Crearla';

  @override
  String projectFolderCreateFailed(String reason) {
    return 'No se pudo crear la carpeta: $reason';
  }

  @override
  String projectFolderCheckFailed(String reason) {
    return 'No se pudo revisar la carpeta: $reason';
  }

  @override
  String get folderBrowserUp => 'Subir una carpeta';

  @override
  String folderBrowserCurrent(String path) {
    return 'Carpeta actual: $path';
  }

  @override
  String folderBrowserOpen(String name) {
    return 'Abrir $name';
  }

  @override
  String get folderBrowserGit => 'Repositorio Git';

  @override
  String get folderBrowserProject => 'Proyecto de OpenCode';

  @override
  String folderBrowserShowInside(String name) {
    return 'Mostrar las carpetas de $name';
  }

  @override
  String get folderBrowserProjectsHere =>
      'Tus proyectos están aquí. Toca uno para abrirlo.';

  @override
  String get folderBrowserHomeHere =>
      'La carpeta personal y la raíz no pueden ser proyectos. Abre una carpeta dentro.';

  @override
  String get folderBrowserNoProjectsTitle => 'Aún no hay proyectos';

  @override
  String get folderBrowserNoProjectsBody => 'Empieza uno para crearlo aquí.';

  @override
  String get folderBrowserEmptyTitle => 'No hay carpetas aquí';

  @override
  String get folderBrowserEmptyBody =>
      'Ábrela como proyecto o sube una carpeta.';

  @override
  String get folderBrowserErrorTitle => 'No se puede mostrar esta carpeta';

  @override
  String get folderBrowserErrorNotInstalled =>
      'Ubuntu aún no está instalado en la app.';

  @override
  String get folderBrowserErrorMissing => 'Ya no existe.';

  @override
  String get folderBrowserErrorDenied => 'La app no tiene permiso para leerla.';

  @override
  String get folderBrowserErrorLinked => 'Es un enlace. Introduce su ruta.';

  @override
  String get folderBrowserErrorFailed =>
      'Inténtalo de nuevo o introduce su ruta.';

  @override
  String get folderBrowserErrorTimedOut =>
      'Tardó demasiado en responder. Inténtalo de nuevo.';

  @override
  String get folderBrowserRetry => 'Intentar de nuevo';

  @override
  String get phoneSetupStartScreenTitle => 'En este teléfono';

  @override
  String get phoneSetupStartHeadline =>
      'Ejecuta un agente de programación aquí mismo';

  @override
  String phoneSetupStartPromise(String size) {
    return 'Sin computadora ni otras apps. Unos $size de descarga la primera vez.';
  }

  @override
  String get phoneSetupStartPromiseNoSize => 'Sin computadora ni otras apps.';

  @override
  String phoneSetupStartSteps(String time) {
    return 'Instalar, nombrar un proyecto, empezar una conversación · $time';
  }

  @override
  String phoneSetupStartStepsTime(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'aprox. $minutes min',
      one: 'aprox. 1 min',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartAboutMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'Aproximadamente $minutes minutos',
      one: 'Aproximadamente un minuto',
    );
    return '$_temp0';
  }

  @override
  String phoneSetupStartMegabytes(String value) {
    return '$value MB';
  }

  @override
  String phoneSetupStartGigabytes(String value) {
    return '$value GB';
  }

  @override
  String get phoneSetupStartSetUp => 'Configurar OpenCode en este teléfono';

  @override
  String phoneSetupStartIncludes(String tools) {
    return 'Incluye $tools.';
  }

  @override
  String phoneSetupStartListPair(String first, String last) {
    return '$first y $last';
  }

  @override
  String get phoneSetupStartListSeparator => ', ';

  @override
  String get phoneSetupStartCustomize => 'Elegir qué instalar';

  @override
  String get phoneSetupStartOtherWays => 'Otras formas';

  @override
  String get phoneSetupStartByAddress =>
      'Conectar a una computadora por dirección';

  @override
  String phoneSetupStartProgressHeadline(int percent) {
    return 'La configuración va al $percent%';
  }

  @override
  String get phoneSetupStartRunningBody =>
      'Sigue avanzando mientras usas otras apps.';

  @override
  String get phoneSetupStartStoppedBody =>
      'Se detuvo antes de terminar. Al continuar se retoma desde donde se quedó.';

  @override
  String get phoneSetupStartContinue => 'Continuar la configuración';

  @override
  String get phoneSetupStartReadyHeadline =>
      'OpenCode está listo en este teléfono';

  @override
  String get phoneSetupStartReadyBody =>
      'Ábrelo para empezar una conversación.';

  @override
  String get phoneSetupStartOpen => 'Abrir';

  @override
  String phoneSetupStartFailed(String reason) {
    return 'No funcionó: $reason';
  }

  @override
  String get phoneSetupStartEntryDetail =>
      'Ejecuta un agente de programación aquí mismo. No necesitas computadora.';

  @override
  String get phoneSetupStartCustomizeTitle => 'Elegir qué instalar';

  @override
  String get phoneSetupStartAddTitle => 'Agregar herramientas';

  @override
  String get phoneSetupStartInstalled => 'Instalado';

  @override
  String phoneSetupStartTotals(String time, String size) {
    return '$time · ~$size';
  }

  @override
  String phoneSetupStartApproxSize(String size) {
    return '~$size';
  }

  @override
  String get phoneSetupStartNothingChosen => 'Aún no has elegido nada';

  @override
  String get phoneSetupStartDone => 'Listo';

  @override
  String get phoneSetupStartAdd => 'Agregar';

  @override
  String get phoneSetupStartChecking => 'Comprobando lo que está instalado…';

  @override
  String get phoneSetupPreflightUnsupportedHeadline =>
      'Este teléfono no puede ejecutarlo';

  @override
  String phoneSetupPreflightUnsupportedBody(String abi) {
    return 'El Ubuntu de esta app solo funciona en teléfonos Arm o Intel de 64 bits; este informa $abi.';
  }

  @override
  String get phoneSetupPreflightLowMemoryHeadline =>
      'Este teléfono no tiene suficiente memoria';

  @override
  String phoneSetupPreflightLowMemoryBody(int minimum, int actual) {
    final intl.NumberFormat minimumNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minimumString = minimumNumberFormat.format(minimum);
    final intl.NumberFormat actualNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String actualString = actualNumberFormat.format(actual);

    return 'OpenCode necesita un teléfono con al menos $minimumString MB de memoria; este tiene $actualString MB. Ejecútalo en una computadora y conecta este teléfono a ella.';
  }

  @override
  String phoneSetupPreflightMayBeSlow(int memory) {
    final intl.NumberFormat memoryNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String memoryString = memoryNumberFormat.format(memory);

    return 'Puede ir lento en este teléfono, que tiene $memoryString MB de memoria.';
  }

  @override
  String get phoneSetupPreflightLowSpaceHeadline =>
      'No hay suficiente espacio libre';

  @override
  String phoneSetupPreflightLowSpaceBody(String size) {
    return 'Libera unos $size en este teléfono y vuelve para configurarlo.';
  }

  @override
  String get phoneSetupPreflightOpenStorage =>
      'Abrir ajustes de almacenamiento';

  @override
  String get phoneSetupOpenWelcomeShowProgress => 'Ver progreso';

  @override
  String phoneSetupOpenPhoneRuntime(String name, String runtime) {
    return '$name · $runtime';
  }

  @override
  String get chatStartBuildWebPage => 'Crea una página web sencilla';

  @override
  String get chatStartPythonScript => 'Escribe un script de Python que…';

  @override
  String get chatStartNodeProject => 'Crea un proyecto de Node.js';

  @override
  String get chatStartReadme => 'Prepara un README';

  @override
  String get chatStartExplainProject => 'Explica este proyecto';

  @override
  String get chatStartWhatChanged => '¿Qué cambió recientemente?';

  @override
  String get chatStartFindBug => 'Encuentra y corrige un error';

  @override
  String get chatStartAddTests => 'Agrega pruebas';

  @override
  String get chatStartListFolder => 'Muestra lo que hay en esta carpeta';

  @override
  String get chatStartEmptyFolder => 'Carpeta vacía';

  @override
  String chatStartItemCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count elementos',
      one: '1 elemento',
    );
    return '$_temp0';
  }

  @override
  String get chatStartGit => 'Git';

  @override
  String chatStartChangeCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count cambios',
      one: '1 cambio',
    );
    return '$_temp0';
  }

  @override
  String get chatStartLooking => 'Mirando la carpeta…';

  @override
  String get chatStartServerFolder => 'Carpeta del servidor';

  @override
  String get chatStartTip =>
      'Escribe / para ver los comandos · mantén pulsado un mensaje para ver sus acciones';

  @override
  String get chatLoadingConversation => 'Cargando la conversación';

  @override
  String get chatLoadFailedTitle => 'No se pudo abrir esta conversación';

  @override
  String get chatLoadFailedBody =>
      'No se perdió nada. Inténtalo de nuevo cuando OpenCode responda.';

  @override
  String get chatSendFailed => 'Tu mensaje no se envió';

  @override
  String get chatSendFailedKept => 'Volvió al cuadro de mensaje.';

  @override
  String get chatStartSuggestionsLabel => 'Formas de empezar';

  @override
  String get perfTraceTitle => 'Rendimiento';

  @override
  String get perfTraceBody =>
      'Cuánto tardó cada paso mientras la app estuvo abierta: conectar, cargar, cada petición al servidor. Solo se conserva en memoria y se vacía al cerrar la app. El informe guarda nombres y tiempos, nunca mensajes ni contraseñas.';

  @override
  String get perfTraceCopy => 'Copiar el informe de tiempos';

  @override
  String get perfTraceCopied => 'Informe de rendimiento copiado';

  @override
  String get perfTraceEmpty => 'Aún nada medido.';

  @override
  String get perfTraceSlowest => 'Los pasos más lentos';

  @override
  String get perfTraceRecent => 'Los pasos más recientes';

  @override
  String perfTraceStatLine(int count, String p50, String p95, String max) {
    return '$count× · típico $p50 · lento $p95 · más largo $max';
  }

  @override
  String perfTraceFailed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count fallidos',
      one: '1 fallido',
    );
    return '$_temp0';
  }

  @override
  String perfTraceAt(String time) {
    return 'a las $time';
  }

  @override
  String perfTraceWithin(String parent) {
    return 'en $parent';
  }

  @override
  String get aiteamComponentTitle => 'AI Team';

  @override
  String aiteamComponentStageDownloading(String index, String total) {
    return 'Descargando AI Team · $index de $total';
  }

  @override
  String get aiteamComponentStagePreparing => 'Preparando AI Team';

  @override
  String aiteamComponentAddingTitle(String names) {
    return 'Agregando $names';
  }

  @override
  String get aiteamComponentNotice =>
      'OpenCode y AI Team se están ejecutando en este teléfono';

  @override
  String get aiteamComponentSectionTitle => 'AI Team en este teléfono';

  @override
  String aiteamComponentOfferBody(String size) {
    return 'Varios agentes reparten el trabajo en un proyecto, aquí mismo. Cerca de $size para descargar.';
  }

  @override
  String get aiteamComponentAdd => 'Agregar AI Team';

  @override
  String aiteamComponentTurnOn(String project) {
    return 'Activar AI Team para $project';
  }

  @override
  String get aiteamComponentTurnOnBody =>
      'El equipo trabaja en sus propias ramas y guarda una copia del historial del proyecto en este teléfono.';

  @override
  String get aiteamComponentNoProject =>
      'Abre primero un proyecto, y luego actívalo para AI Team en él.';

  @override
  String get aiteamComponentStageTeam => 'Preparando el equipo';

  @override
  String aiteamComponentStageProject(String project) {
    return 'Agregando $project';
  }

  @override
  String get aiteamComponentStageStarting => 'Iniciando AI Team';

  @override
  String get aiteamComponentStageWaiting => 'Esperando que AI Team responda';

  @override
  String get aiteamComponentTurnOnExpectation =>
      'Esto toma de 5 a 10 minutos la primera vez. Puedes salir de esta pantalla; sigue en marcha.';

  @override
  String get aiteamComponentStartExpectation =>
      'Esto toma unos minutos. Puedes salir de esta pantalla; sigue en marcha.';

  @override
  String aiteamComponentStageSoFar(String time) {
    return '$time hasta aquí';
  }

  @override
  String aiteamComponentStageTook(String time) {
    return 'Tomó $time';
  }

  @override
  String get aiteamComponentRunning => 'AI Team · En marcha';

  @override
  String get aiteamComponentStopped => 'AI Team · Detenido';

  @override
  String aiteamComponentProjects(String projects) {
    return 'Trabaja en $projects';
  }

  @override
  String get aiteamComponentStart => 'Iniciar AI Team';

  @override
  String aiteamComponentFailed(String reason) {
    return 'AI Team no pudo iniciar: $reason';
  }

  @override
  String get aiteamComponentFailedExited => 'se detuvo por sí mismo';

  @override
  String get aiteamComponentFailedTimeout => 'no respondió a tiempo';

  @override
  String get aiteamComponentShowDetails => 'Mostrar detalles';

  @override
  String get aiteamComponentChildProcesses =>
      'Android detiene los programas extra de una app cuando corre muchos a la vez, y un equipo corre varios. Si el equipo se detiene mientras trabaja, activa Opciones de desarrollador › Desactivar restricciones de procesos hijos.';

  @override
  String workOpenLiveConversation(String title) {
    return 'Abrir “$title”';
  }

  @override
  String get workLoadingLabel => 'Cargando';

  @override
  String agentNotAnsweringPhone(String agent) {
    return '$agent en este teléfono no responde';
  }

  @override
  String get workServerNotAnsweringPhone =>
      'OpenCode en este teléfono no responde';

  @override
  String workServerNotAnswering(String server) {
    return '$server no responde';
  }

  @override
  String get workServerKeepsTrying =>
      'La app sigue intentándolo en segundo plano.';

  @override
  String get workServerRestart => 'Reiniciar';

  @override
  String get connectionRestartAgent => 'Reiniciar Claude Code';

  @override
  String get connectionRestartPhoneServer => 'Reiniciar OpenCode';

  @override
  String get workServerRestartTitle => '¿Reiniciar OpenCode en este teléfono?';

  @override
  String get workServerRestartBody =>
      'Se detendrá el turno del agente en curso. Tus conversaciones se conservan.';

  @override
  String get connectStartingPhone => 'Iniciando OpenCode en este teléfono…';

  @override
  String get connectStartingBody =>
      'Tus conversaciones se conservan. Esto puede tardar un minuto.';

  @override
  String aiteamBringInDone(String project, String commit) {
    return '$project tiene el último trabajo del equipo ($commit).';
  }

  @override
  String aiteamBringInDirty(String commit, String project, String files) {
    return 'El trabajo del equipo ($commit) no está todavía en $project: $project tiene cambios propios ($files), así que se dejó como está.';
  }

  @override
  String aiteamBringInDiverged(String commit, String project) {
    return 'El trabajo del equipo ($commit) no está en $project: $project tiene commits propios. Fusiona los dos con git.';
  }

  @override
  String aiteamBringInFailed(String project, String reason) {
    return 'El trabajo del equipo no pudo entrar a $project: $reason';
  }

  @override
  String aiteamBringInAction(String project) {
    return 'Traer el trabajo del equipo a $project';
  }

  @override
  String get teamUiHostPhrasePhone => 'En este teléfono';

  @override
  String teamUiHostPhraseComputerNamed(String name) {
    return 'En $name';
  }

  @override
  String get teamUiHostPhraseComputer => 'En tu computadora';

  @override
  String get teamUiHostPhrasePaused => 'Pausado';

  @override
  String get teamUiHostPhraseNotAnswering => 'No responde';

  @override
  String teamUiTaskSteps(int done, int total) {
    String _temp0 = intl.Intl.pluralLogic(
      total,
      locale: localeName,
      other: '$done de $total pasos hechos',
      one: '$done de 1 paso hecho',
    );
    return '$_temp0';
  }

  @override
  String teamUiTaskDoneAgo(String when) {
    return 'Terminado $when';
  }

  @override
  String teamUiTaskMergedAgo(String when) {
    return 'Terminado · fusionado $when';
  }

  @override
  String teamUiTaskCancelledAgo(String when) {
    return 'Cancelado $when';
  }

  @override
  String teamUiHomeDoneMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar $count más',
      one: 'Mostrar 1 más',
    );
    return '$_temp0';
  }

  @override
  String get teamUiHomeSearchClose => 'Cerrar la búsqueda';

  @override
  String get teamUiHomeNeedsYouAnswer => 'Responder';

  @override
  String teamUiHomeNeedsYouAnnouncement(String question) {
    return 'Requiere tu atención: $question';
  }

  @override
  String get teamUiAgentRoleWorker => 'Ejecutor';

  @override
  String get teamUiAgentRoleReviewer => 'Revisor';

  @override
  String get teamUiAgentRolePlanner => 'Planificador';

  @override
  String get teamUiAgentRoleSupervisor => 'Supervisor';

  @override
  String get teamUiAgentRoleHelper => 'Ayudante';

  @override
  String get teamUiAgentRoleOther => 'Agente';

  @override
  String get teamUiRunStageWaiting => 'Planificada';

  @override
  String get teamUiRunStageWorking => 'Trabajando';

  @override
  String get teamUiRunStageReviewing => 'En revisión';

  @override
  String get teamUiRunStageDone => 'Fusionada';

  @override
  String teamUiRunStageSemantics(int position, String stage) {
    return 'Etapa $position de 4: $stage';
  }

  @override
  String get teamUiRunStepsHeading => 'Pasos';

  @override
  String get teamUiRunDetailsUsage => 'Consumo';

  @override
  String get serverRowConnected => 'Conectado';

  @override
  String get localAgentPageTitle => 'Claude Code';

  @override
  String pluginsTeamRowFound(String server) {
    return 'Encontrado en $server';
  }

  @override
  String get pluginsLoading => 'Cargando plugins';

  @override
  String get pluginsBuiltinGroup => 'Incluidos';

  @override
  String pluginsBuiltinActive(int count) {
    return '$count activos';
  }

  @override
  String pluginsBuiltinFailed(int count) {
    return 'A $count les falló cargar';
  }

  @override
  String get pluginsStatusFailedToLoad => 'Fallo al cargar';

  @override
  String get pluginsDetailsId => 'ID';

  @override
  String get localTerminalSourcePhone => 'Linux integrado';

  @override
  String get localTerminalSourceServer => 'Servidor de OpenCode';

  @override
  String localTerminalShellName(int number) {
    return 'Shell $number';
  }

  @override
  String get localTerminalNewShell => 'Shell nueva';

  @override
  String get localTerminalStopShell => 'Detener esta shell';

  @override
  String get localTerminalCloseShell => 'Cerrar esta shell';

  @override
  String get localTerminalStopBody =>
      'Los programas que corren en ella se detienen también.';

  @override
  String get localTerminalStop => 'Detener';

  @override
  String get localTerminalStarting => 'Iniciando el shell';

  @override
  String get localTerminalNotSetUpTitle =>
      'Linux no está configurado en este teléfono';

  @override
  String get localTerminalNotSetUpBody =>
      'La terminal corre en el Linux que la configuración del teléfono instala.';

  @override
  String get localTerminalEndedTitle => 'La shell terminó';

  @override
  String localTerminalEndedBody(int code) {
    return 'Salió con el código $code.';
  }

  @override
  String get localTerminalRestart => 'Reiniciar';

  @override
  String get localTerminalFailedTitle => 'El shell no arrancó';

  @override
  String get localTerminalFailedBody =>
      'Inténtalo de nuevo. Si sigue fallando, Detalles explica por qué.';

  @override
  String get localTerminalTryAgain => 'Reintentar';

  @override
  String localTerminalCost(int perShell, int limit) {
    return 'Cada shell corre $perShell programas. Con AI Team activado, Android puede detener los programas de la app desde $limit en adelante.';
  }

  @override
  String localTerminalCostNow(int perShell, int count, int limit) {
    return 'Cada shell corre $perShell programas. Con AI Team activado, la app corre $count; Android puede detenerlos desde $limit en adelante.';
  }

  @override
  String get localTerminalKeysLabel => 'Teclas de la terminal';

  @override
  String get localTerminalKeyCtrl => 'Control';

  @override
  String get localTerminalKeyAlt => 'Alt';

  @override
  String get localTerminalKeyPageUp => 'Página arriba';

  @override
  String get localTerminalKeyPageDown => 'Página abajo';

  @override
  String get localTerminalSemantics =>
      'Terminal en este teléfono. Toca para escribir; mantén pulsado para seleccionar texto.';

  @override
  String get globalSessionsLoadFailedTitle =>
      'No se pudieron cargar las conversaciones';

  @override
  String get filesLoadFailedTitle => 'No se pudo abrir esta carpeta';

  @override
  String get filesSymbolsFailedTitle => 'No se pudieron buscar los símbolos';

  @override
  String get terminalListFailedTitle => 'No se pudieron listar los terminales';

  @override
  String get addServerConnectTo => 'Conectar con';

  @override
  String get addServerTypeOpenCode => 'OpenCode en una computadora';

  @override
  String get addServerTypeOpenCodeDetail =>
      'Empareja con un código o introduce su dirección';

  @override
  String get addServerTypeCodex => 'Codex';

  @override
  String get addServerTypeCodexDetail =>
      'El app-server de Codex en tu computadora';

  @override
  String get addServerTypePaseo => 'Claude Code o Pi';

  @override
  String get addServerTypePaseoDetail => 'A través de Paseo en tu computadora';

  @override
  String get addServerScan => 'Escanear código';

  @override
  String get addServerPaste => 'Pegar código';

  @override
  String get addServerManual => 'Ingresar la dirección';

  @override
  String get addServerChecking => 'Comprobando la conexión…';

  @override
  String addServerCheckingHost(String host) {
    return 'Comprobando $host…';
  }

  @override
  String addServerConnectingHost(String host) {
    return 'Conectando a $host…';
  }

  @override
  String get addServerSaveAnyway => 'Guardar de todos modos';

  @override
  String get effectsSection => 'Efectos';

  @override
  String get effectsAnimations => 'Movimiento';

  @override
  String get effectsMotionFull => 'Completa';

  @override
  String get effectsMotionFullHint =>
      'Los dibujos se mueven, las pantallas de espera respiran y los momentos terminados se celebran';

  @override
  String get effectsMotionCalm => 'Tranquila';

  @override
  String get effectsMotionCalmHint =>
      'Los dibujos aparecen, nada se mueve de forma continua y nada celebra';

  @override
  String get effectsMotionOff => 'Desactivada';

  @override
  String get effectsMotionOffHint => 'Todo aparece de inmediato';

  @override
  String get effectsMotionSystemOff =>
      'La opción Quitar animaciones de tu teléfono está activada, así que nada se mueve elijas lo que elijas aquí';

  @override
  String get effectsActivityGlow => 'Borde brillante al responder';

  @override
  String get effectsSaveFailed =>
      'No se pudo guardar esta opción en este dispositivo. Inténtalo de nuevo.';

  @override
  String get teamDiscoverEntryTitle =>
      'Darle un trabajo más grande a un equipo';

  @override
  String get teamDiscoverIntroBody =>
      'Describe lo que quieres hecho. Un equipo de agentes lo parte en pasos, los trabaja lado a lado y trae el trabajo terminado a tu proyecto.';

  @override
  String get teamDiscoverHowHeading => 'Cómo funciona';

  @override
  String get teamDiscoverStepPlanTitle => 'Planifica';

  @override
  String get teamDiscoverStepPlanBody =>
      'Un planificador reparte tu trabajo en pasos.';

  @override
  String get teamDiscoverStepWorkTitle => 'Trabaja';

  @override
  String get teamDiscoverStepWorkBody =>
      'Los ejecutores toman los pasos, cada uno en su propia copia del proyecto.';

  @override
  String get teamDiscoverStepCheckTitle => 'Revisa';

  @override
  String get teamDiscoverStepCheckBody =>
      'Un revisor revisa el trabajo de cada paso.';

  @override
  String get teamDiscoverStepMergeTitle => 'Fusiona';

  @override
  String get teamDiscoverStepMergeBody =>
      'El trabajo terminado entra a tu proyecto. Cuando necesita una decisión, te pregunta.';

  @override
  String get teamDiscoverNeedsPhone => 'Qué necesita en este teléfono';

  @override
  String teamDiscoverNeedsServer(String server) {
    return 'Qué necesita en $server';
  }

  @override
  String teamDiscoverDownloadTitle(String size) {
    return 'Cerca de $size para descargar';
  }

  @override
  String get teamDiscoverInAppDownloadBody =>
      'Se instala una vez, junto a OpenCode en este teléfono.';

  @override
  String get teamDiscoverBatteryTitle => 'Más batería mientras trabaja';

  @override
  String get teamDiscoverInAppBatteryBody =>
      'Varios agentes corren a la vez, y Android puede detener a algunos si corren demasiados.';

  @override
  String get teamDiscoverProjectTitle => 'Tú eliges los proyectos';

  @override
  String get teamDiscoverProjectBody =>
      'Actívalo para cada proyecto en el que quieras que trabaje.';

  @override
  String teamDiscoverComputerTitle(String server) {
    return 'Corre en $server';
  }

  @override
  String get teamDiscoverComputerBody =>
      'Instala Gas City ahí una vez; la app lo encuentra sola.';

  @override
  String get teamDiscoverSpeedTitle => 'Tan rápido como tu computadora';

  @override
  String get teamDiscoverSpeedBody =>
      'Manténlo despierto mientras el equipo trabaja.';

  @override
  String teamDiscoverLooking(String server) {
    return 'Buscándolo en $server…';
  }

  @override
  String get teamDiscoverFoundBody => 'Está listo para activarse.';

  @override
  String get teamDiscoverEnterAddress => 'Escribe su dirección';

  @override
  String get teamDiscoverOnComputer => 'Hazlo correr en una computadora';

  @override
  String get teamDiscoverTurningOn => 'Activando…';

  @override
  String get teamNowChecksEveryMinute => 'el equipo revisa cada minuto';

  @override
  String teamNowChecksEvery(String minutes) {
    return 'el equipo revisa cada $minutes min';
  }

  @override
  String get teamNowNextCheck =>
      'un ejecutor arranca en la próxima revisión del equipo';

  @override
  String get teamNowNoWorkerStarted => 'ningún ejecutor ha arrancado';

  @override
  String get teamNowPausedLine =>
      'El equipo está pausado · nada arranca hasta que lo reanudes';

  @override
  String get teamNowStartWorker => 'Arrancar un ejecutor';

  @override
  String get teamNowWhy => '¿Por qué?';

  @override
  String get teamAgentDidNotStartTitle => 'El ejecutor no arrancó';

  @override
  String get teamAgentDidNotStartBody =>
      'Hay una tarea en espera, pero este ejecutor no corre.';

  @override
  String get teamAgentStartIt => 'Arrancarlo';

  @override
  String teamOutputNotRunning(String name) {
    return '$name no corre, así que no hay salida';
  }

  @override
  String get teamOutputSilent =>
      'Aún sin salida · puede tomar un minuto arrancar';

  @override
  String teamAgentTitle(String role, String name) {
    return '$role · $name';
  }

  @override
  String teamAgentWorksOn(String title) {
    return 'En “$title”';
  }

  @override
  String teamOutputStartingPhone(String age) {
    return 'Arrancando · $age hasta ahora · esto puede tomar unos minutos en un teléfono';
  }

  @override
  String get chatWatchEmptyTitle => 'Aún nada aquí';

  @override
  String get chatWatchEmptyBody =>
      'Esta conversación se va llenando mientras el agente trabaja.';

  @override
  String teamWatchBannerRole(String role, String state) {
    return 'Observando $role · $state';
  }

  @override
  String get teamWatchFallbackUnreadable =>
      'Su conversación no se puede leer desde el servidor al que esta app está conectada, así que esto es la salida en vivo del equipo.';

  @override
  String get teamWatchFallbackNotFound =>
      'Su conversación aún no está en el servidor al que esta app está conectada (puede seguir arrancando, o el equipo corre en otra computadora), así que esto es la salida en vivo del equipo.';

  @override
  String get teamOpenConversation => 'Abrir la conversación';

  @override
  String teamOpenConversationHint(String name) {
    return 'Observar el trabajo de $name en el chat';
  }

  @override
  String get teamOpenConversationFinding => 'Buscando su conversación…';

  @override
  String get teamChatUntitled => 'Tarea del equipo';

  @override
  String teamChatSubtitle(String host) {
    return 'AI Team · $host';
  }

  @override
  String get teamChatOpenTeam => 'AI Team';

  @override
  String get teamChatTaskDetails => 'Detalles de la tarea';

  @override
  String get teamChatStopTask => 'Detener la tarea';

  @override
  String get teamChatStopConfirmTitle => '¿Detener esta tarea?';

  @override
  String teamChatStopConfirmBody(String task) {
    return '“$task” se detiene en la computadora del equipo. Sus ejecutores aún en marcha se detienen ahora; el trabajo ya terminado se queda. Esto no se puede deshacer desde el teléfono.';
  }

  @override
  String get teamChatStopKeepRunning => 'Seguir corriendo';

  @override
  String get teamChatLoading => 'Cargando la tarea';

  @override
  String get teamChatLeadName => 'El equipo';

  @override
  String get teamChatLeadSent => 'Enviado al equipo';

  @override
  String get teamChatLeadNothingYet =>
      'Nada todavía. El equipo no ha planificado esta tarea.';

  @override
  String teamChatLeadPlanned(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Planificó $count pasos',
      one: 'Planificó 1 paso',
    );
    return '$_temp0';
  }

  @override
  String teamChatLeadRouted(String title) {
    return 'Envió “$title” a los ejecutores';
  }

  @override
  String teamChatLeadStarting(String title) {
    return 'Un ejecutor arrancó en “$title”';
  }

  @override
  String teamChatLeadClaimedWorker(String title) {
    return 'Un ejecutor tomó “$title”';
  }

  @override
  String teamChatLeadPushed(String title) {
    return 'Los cambios de “$title” están en una rama';
  }

  @override
  String teamChatLeadReview(String title) {
    return 'Entregó “$title” a revisión';
  }

  @override
  String teamChatLeadMerged(String title) {
    return 'Fusionó “$title”';
  }

  @override
  String teamChatLeadStepFailed(String title) {
    return '“$title” falló';
  }

  @override
  String teamChatLeadStepCancelled(String title) {
    return '“$title” se canceló';
  }

  @override
  String teamChatLeadNeedsYou(String question) {
    return 'Requiere tu atención: $question';
  }

  @override
  String get teamChatLeadTaskMerged => 'Fusionado. La tarea está lista.';

  @override
  String get teamChatLeadTaskFinished => 'La tarea está lista.';

  @override
  String get teamChatLeadTaskFailed => 'La tarea falló.';

  @override
  String get teamChatLeadTaskCancelled => 'La tarea se canceló.';

  @override
  String get teamChatAWorker => 'Un ejecutor';

  @override
  String teamChatStepsSummary(int count, int done) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pasos',
      one: '1 paso',
    );
    return '$_temp0 · $done hechos';
  }

  @override
  String get teamChatComposerHint => 'Escribirle al equipo…';

  @override
  String teamChatComposerGoesTo(String name) {
    return 'Tu mensaje va a $name';
  }

  @override
  String get teamChatComposerNobody =>
      'Aún no hay ningún agente en esta tarea a quien escribirle.';

  @override
  String get teamChatComposerCannot =>
      'A este equipo no se le puede escribir desde aquí.';

  @override
  String get teamBoardTitle => 'Tablero';

  @override
  String get teamBoardOpenTooltip => 'Tablero';

  @override
  String get teamBoardColumnBacklog => 'Backlog';

  @override
  String get teamBoardColumnReady => 'Listas';

  @override
  String get teamBoardColumnWorking => 'En marcha';

  @override
  String get teamBoardColumnReview => 'Revisión';

  @override
  String get teamBoardColumnDone => 'Terminadas';

  @override
  String get teamBoardEmptyBacklogTitle => 'Nada en espera';

  @override
  String get teamBoardEmptyBacklogBody =>
      'Las tareas que agregues y aún no hayas iniciado esperan aquí.';

  @override
  String get teamBoardEmptyReadyTitle => 'Nada en fila';

  @override
  String get teamBoardEmptyReadyBody =>
      'Las tareas entregadas al equipo esperan aquí por un ejecutor.';

  @override
  String get teamBoardEmptyWorkingTitle => 'Nadie está trabajando';

  @override
  String get teamBoardEmptyWorkingBody =>
      'Una tarea entra aquí cuando un ejecutor la toma.';

  @override
  String get teamBoardEmptyReviewTitle => 'Nada por revisar';

  @override
  String get teamBoardEmptyReviewBody =>
      'El trabajo terminado espera aquí por su revisión y fusión.';

  @override
  String get teamBoardEmptyDoneTitle => 'Nada terminado esta semana';

  @override
  String get teamBoardEmptyDoneBody =>
      'Las tareas fusionadas, terminadas y canceladas de los últimos 7 días se muestran aquí.';

  @override
  String get teamBoardEmptyTitle => 'Aún no hay tareas';

  @override
  String get teamBoardEmptyBody =>
      'Las tareas que le des al equipo aparecen aquí, según su estado.';

  @override
  String get teamBoardPriorityUrgent => 'Urgente';

  @override
  String get teamBoardPriorityHigh => 'Alta';

  @override
  String get teamBoardPriorityNormal => 'Normal';

  @override
  String get teamBoardPriorityLow => 'Baja';

  @override
  String get teamBoardPrioritySomeday => 'Algún día';

  @override
  String get teamBoardTypeBug => 'Error';

  @override
  String get teamBoardTypeFeature => 'Función';

  @override
  String get teamBoardTypeEpic => 'Epic';

  @override
  String get teamBoardTypeChore => 'Tarea de mantenimiento';

  @override
  String teamBoardEpicProgress(int done, int total) {
    return '$done de $total hechos';
  }

  @override
  String get teamBoardFlagNeedsYou => 'Requiere tu atención';

  @override
  String get teamBoardFlagBlocked => 'Bloqueada';

  @override
  String teamBoardFlagBlockedBy(String title) {
    return 'Bloqueada por $title';
  }

  @override
  String teamBoardFlagBlockedByMore(String title, int count) {
    return 'Bloqueada por $title + $count más';
  }

  @override
  String get teamBoardFlagFailed => 'Se detuvo con un error';

  @override
  String teamBoardFlagInEpic(String epic) {
    return 'En $epic';
  }

  @override
  String teamBoardFlagMoving(String column) {
    return 'Moviendo a $column…';
  }

  @override
  String get teamBoardFlagCancelled => 'Cancelada';

  @override
  String get teamBoardMoveMenuTooltip => 'Mover o cambiar';

  @override
  String teamBoardMoveSheetWhere(String column) {
    return 'En $column';
  }

  @override
  String get teamBoardMoveStartNow => 'Iniciar ahora';

  @override
  String get teamBoardMoveStartNowHint =>
      'Entregarla a los ejecutores del equipo';

  @override
  String get teamBoardMoveBackToBacklog => 'Devolver al Backlog';

  @override
  String get teamBoardMoveBackToBacklogHint =>
      'El equipo no la tomará hasta que la inicies';

  @override
  String get teamBoardMovePriority => 'Prioridad';

  @override
  String get teamBoardMoveCancel => 'Cancelar la tarea';

  @override
  String get teamBoardMoveCancelHint => 'La pasa a Terminadas como cancelada';

  @override
  String get teamBoardMoveReopen => 'Ponerla de nuevo en el Backlog';

  @override
  String get teamBoardMoveReopenHint =>
      'Nadie trabaja en ella hasta que la inicies';

  @override
  String get teamBoardOpenConversation => 'Abrir la conversación';

  @override
  String get teamBoardOpenDetails => 'Abrir los detalles';

  @override
  String get teamBoardTeamMoves =>
      'El equipo mueve esta tarea. Abre su conversación para escribirle al equipo o detenerla.';

  @override
  String get teamBoardReadOnlyNote =>
      'Este host no le permite a la app cambiar tareas, así que el tablero es de solo lectura aquí.';

  @override
  String get teamBoardStatusReadOnly =>
      'Solo lectura aquí · este host no le permite a la app cambiar tareas';

  @override
  String teamBoardCancelTitle(String title) {
    return '¿Cancelar “$title”?';
  }

  @override
  String get teamBoardCancelBody =>
      'El equipo no trabajará en ella. Va a Terminadas como cancelada, y después puedes devolverla al Backlog.';

  @override
  String get teamBoardCancelKeep => 'Conservarla';

  @override
  String teamBoardMoveFailedTitle(String title) {
    return 'No se pudo cambiar “$title”';
  }

  @override
  String get teamBoardMoveFailedBody =>
      'El host del equipo dijo que no, así que se queda donde estaba.';

  @override
  String get teamBoardPriorityTitle => 'Prioridad';

  @override
  String get teamBoardAddFailed =>
      'No se pudo agregar. El host del equipo dijo que no.';

  @override
  String get teamBoardProjectTooltip => 'Elegir proyecto';

  @override
  String appExitForceStopped(String time) {
    return 'OpenCode Mobile se cerró $time';
  }

  @override
  String appExitLowMemory(String time) {
    return 'Android cerró OpenCode Mobile $time para liberar memoria';
  }

  @override
  String appExitCrashed(String time) {
    return 'OpenCode Mobile se detuvo de forma inesperada $time';
  }

  @override
  String appExitKilled(String time) {
    return 'Android detuvo OpenCode Mobile $time';
  }

  @override
  String appExitServerStopped(String what) {
    return '$what. El OpenCode de tu teléfono se detuvo con ella; está iniciando de nuevo.';
  }

  @override
  String appExitServerAndTeamStopped(String what) {
    return '$what. El OpenCode de tu teléfono y el AI Team se detuvieron con ella; están iniciando de nuevo.';
  }

  @override
  String appExitServerStoppedManual(String what) {
    return '$what. El OpenCode de tu teléfono se detuvo con ella. Inícialo de nuevo cuando quieras.';
  }

  @override
  String appExitServerAndTeamStoppedManual(String what) {
    return '$what. El OpenCode de tu teléfono y el AI Team se detuvieron con ella. Inícialos de nuevo cuando quieras.';
  }

  @override
  String appExitServerBack(String what) {
    return '$what. El OpenCode de tu teléfono se detuvo con ella y ya está en ejecución de nuevo.';
  }

  @override
  String appExitServerBackTeam(String what) {
    return '$what. El OpenCode de tu teléfono y el AI Team se detuvieron con ella; OpenCode ya está en ejecución de nuevo.';
  }

  @override
  String appExitAtTime(String time) {
    return 'a las $time';
  }

  @override
  String appExitOnDay(String day, String time) {
    return 'el $day a las $time';
  }

  @override
  String get appExitSwitchToPhone => 'Cambiar a este teléfono';

  @override
  String get appExitKeepRunning => 'Mantener en ejecución';

  @override
  String get keepRunningTitle => 'Mantener en ejecución en segundo plano';

  @override
  String get keepRunningRowSubtitle =>
      'Qué permitir para que tu teléfono no cierre la app';

  @override
  String get keepRunningIntro =>
      'El OpenCode de tu teléfono y el AI Team se ejecutan dentro de esta app, así que se detienen cuando Android la cierra. En este teléfono, permite lo siguiente:';

  @override
  String get keepRunningSwipeWarning =>
      'Este teléfono cierra una app que deslizas fuera de Apps recientes, incluso mientras trabaja. Bloquéala ahí en lugar de deslizarla.';

  @override
  String get keepRunningBatteryTitle => 'No optimizar la batería';

  @override
  String get keepRunningBatteryDetail =>
      'Permite que la app siga en ejecución mientras usas otras apps.';

  @override
  String get keepRunningBatteryDone => 'Permitido';

  @override
  String get keepRunningLockTitle => 'Bloquéala en Apps recientes';

  @override
  String get keepRunningLockNubia =>
      'Abre Apps recientes y desliza la tarjeta de OpenCode Mobile hacia abajo hasta que aparezca el candado.';

  @override
  String get keepRunningLockSamsung =>
      'Abre Apps recientes, toca el ícono de OpenCode Mobile sobre su tarjeta y elige Mantener abierta.';

  @override
  String get keepRunningLockOther =>
      'Abre Apps recientes, mantén pulsada la tarjeta de OpenCode Mobile y toca el candado.';

  @override
  String get keepRunningAutostartTitle => 'Permitir inicio automático';

  @override
  String get keepRunningAutostartDetail =>
      'Actívalo para OpenCode Mobile para que el teléfono no la detenga en segundo plano.';

  @override
  String get keepRunningAutostartHuawei =>
      'En Inicio de apps, configura OpenCode Mobile en Administrar manualmente y activa los tres interruptores.';

  @override
  String get keepRunningBackgroundTitle =>
      'Permitir actividad en segundo plano';

  @override
  String get keepRunningBackgroundXiaomi =>
      'En Información de la app › Ahorro de batería, elige Sin restricciones.';

  @override
  String get keepRunningBackgroundOppo =>
      'En Información de la app › Uso de batería, permite la actividad en segundo plano.';

  @override
  String get keepRunningBackgroundVivo =>
      'En Información de la app › Batería, permite el alto consumo en segundo plano.';

  @override
  String get keepRunningBackgroundSamsung =>
      'En Información de la app › Batería, elige Sin restricciones y mantén la app fuera de Apps en suspensión.';

  @override
  String get keepRunningBackgroundOther =>
      'En Información de la app › Batería, elige Sin restricciones o permite la ejecución en segundo plano.';

  @override
  String get keepRunningOpen => 'Abrir';

  @override
  String get keepRunningOpenFailed =>
      'Este teléfono no tiene esa pantalla. Abre Ajustes › Apps › OpenCode Mobile.';

  @override
  String get keepRunningFootnote =>
      'Si Android sigue cerrando la app, volverá a iniciar el OpenCode de tu teléfono la próxima vez que la abras.';

  @override
  String teamAgentLastStep(String step) {
    return 'Último paso: $step';
  }

  @override
  String teamAgentLastActive(String elapsed) {
    return 'activo hace $elapsed';
  }

  @override
  String teamChatLeadEarlier(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count actualizaciones anteriores',
      one: '1 actualización anterior',
    );
    return '$_temp0';
  }

  @override
  String get appExitQuietRestarted => 'Se reinició después de que la app cerró';

  @override
  String get appExitQuietRestarting =>
      'Reiniciando después de que la app cerró';

  @override
  String get thermalQuietPaused =>
      'El teléfono está caliente · AI Team pausado hasta que se enfríe';

  @override
  String get thermalQuietStopped =>
      'El teléfono está muy caliente · AI Team detenido hasta que se enfríe';

  @override
  String get thermalQuietResumed => 'El teléfono se enfrió · AI Team reanudado';

  @override
  String get thermalPausedNotice =>
      'Tu teléfono está caliente: se pausó el AI Team para que se enfríe. Se reanuda solo.';

  @override
  String get thermalStoppedNotice =>
      'Tu teléfono está muy caliente: se detuvo el AI Team para protegerlo. Su trabajo se conserva y vuelve a empezar cuando el teléfono se enfríe.';

  @override
  String get thermalResumedNotice =>
      'AI Team reanudado: tu teléfono se ha enfriado.';

  @override
  String get thermalGuardSetting =>
      'Pausar el AI Team cuando el teléfono esté caliente';

  @override
  String get thermalGuardSettingDetail =>
      'El equipo se pausa conservando su trabajo y se reanuda solo cuando el teléfono se enfría.';

  @override
  String get kitSheetClose => 'Cerrar';

  @override
  String get kitSheetDismiss => 'Cerrar';

  @override
  String get kitSheetLoading => 'Cargando';

  @override
  String get kitConfirmCancel => 'Cancelar';

  @override
  String get kitConfirmKeepRunning => 'Mantener en ejecución';

  @override
  String get kitConfirmKeepEditing => 'Seguir editando';

  @override
  String kitConfirmTypeName(String name) {
    return 'Escribe $name para confirmar';
  }

  @override
  String get kitConfirmTypeNameReason =>
      'Escribe el nombre exactamente como se muestra para activarlo.';

  @override
  String get kitConfirmFailed => 'No se completó. Puedes intentarlo de nuevo.';

  @override
  String get kitTryAgain => 'Intentar de nuevo';

  @override
  String get kitDetails => 'Detalles';

  @override
  String get kitDiscardTitle => '¿Descartar tus cambios?';

  @override
  String get kitDiscardBody =>
      'Lo que cambiaste aquí no se ha guardado. Descartarlo no se puede deshacer.';

  @override
  String get kitDiscardConfirm => 'Descartar cambios';

  @override
  String get kitCopied => 'Copiado';

  @override
  String get kitMore => 'Más';

  @override
  String kitChipRemove(String label) {
    return 'Quitar $label';
  }

  @override
  String get kitCopy => 'Copiar';

  @override
  String get kitWorking => 'Trabajando';

  @override
  String get kitImageUnavailable => 'No se puede mostrar esta imagen';

  @override
  String get kitZoomIn => 'Acercar';

  @override
  String get kitZoomOut => 'Alejar';

  @override
  String get kitZoomReset => 'Reajustar el zoom';

  @override
  String get kitZoomFit => 'Ajustar a la pantalla';

  @override
  String get kitZoomAtStart => 'Ya está en la vista completa';

  @override
  String get kitZoomAtMax => 'El zoom máximo';

  @override
  String kitZoomLevel(String percent) {
    return '$percent %';
  }

  @override
  String kitZoomShortcut(String action, String key) {
    return '$action · Ctrl+$key';
  }

  @override
  String get kitMenu => 'Menú';

  @override
  String get kitCopyDetails => 'Copiar detalles';

  @override
  String get kitReportProblem => 'Informar de un problema';

  @override
  String kitProgressStep(int step, int of) {
    final intl.NumberFormat stepNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String stepString = stepNumberFormat.format(step);
    final intl.NumberFormat ofNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String ofString = ofNumberFormat.format(of);

    return 'Paso $stepString de $ofString';
  }

  @override
  String kitProgressEtaSeconds(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    String _temp0 = intl.Intl.pluralLogic(
      seconds,
      locale: localeName,
      other: 'faltan unos $secondsString s',
    );
    return '$_temp0';
  }

  @override
  String kitProgressEtaMinutes(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'faltan unos $minutesString min',
    );
    return '$_temp0';
  }

  @override
  String kitProgressEtaHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'faltan unas $hoursString h',
    );
    return '$_temp0';
  }

  @override
  String get kitQrTooLong =>
      'Es demasiado largo para un código QR. Copia el enlace.';

  @override
  String kitSinceStillWaiting(int seconds) {
    final intl.NumberFormat secondsNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String secondsString = secondsNumberFormat.format(seconds);

    return 'Siguen esperando después de $secondsString s';
  }

  @override
  String kitSinceWaitingFor(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'Esperando $minutesString min',
      one: 'Esperando 1 min',
      zero: 'Esperando menos de un minuto',
    );
    return '$_temp0';
  }

  @override
  String kitSinceAge(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutesString min',
      one: '1 min',
      zero: 'menos de un minuto',
    );
    return '$_temp0';
  }

  @override
  String get kitMarkWaiting => 'En espera';

  @override
  String get kitMarkWorking => 'Trabajando';

  @override
  String get kitMarkDone => 'Listo';

  @override
  String get kitMarkFailed => 'Falló';

  @override
  String get kitMarkPaused => 'En pausa';

  @override
  String get kitTaskNeedsYou => 'Te necesita';

  @override
  String get kitTaskStopped => 'Detenido';

  @override
  String get kitSwatchInUse => 'En uso';

  @override
  String get kitThemePreviewTitle => 'Arreglar el error de inicio de sesión';

  @override
  String get kitThemePreviewWorking => 'Trabajando · 2 min';

  @override
  String get kitThemePreviewNeedsYou => 'Te necesita';

  @override
  String get kitThemePreviewPrimary => 'Enviar';

  @override
  String get kitThemePreviewSecondary => 'Adjuntar';

  @override
  String get kitThemePreviewSegment => 'Activado';

  @override
  String get kitThemePreviewCode => 'final ready = true;';

  @override
  String get kitTerminalViewKeySlash => 'Tecla barra diagonal';

  @override
  String get kitTerminalViewKeyDash => 'Tecla guion';

  @override
  String get kitTerminalViewKeyPipe => 'Tecla barra vertical';

  @override
  String get kitTerminalViewKeyTilde => 'Tecla tilde';

  @override
  String get kitTerminalViewKeyHome => 'Tecla Home';

  @override
  String get kitTerminalViewKeyEnd => 'Tecla End';

  @override
  String kitTerminalViewShowingLast(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrando las últimas $countString líneas',
      one: 'Mostrando la última línea',
    );
    return '$_temp0';
  }

  @override
  String get kitUndoAction => 'Deshacer';

  @override
  String kitUndoFailed(String message) {
    return 'No se pudo deshacer. $message';
  }

  @override
  String get kitUndoWorking => 'Deshaciendo';

  @override
  String get kitTermHint => 'Hay explicación disponible';

  @override
  String get kitTermShow => 'Mostrar la explicación';

  @override
  String get kitTermClose => 'Cerrar la explicación';

  @override
  String kitFieldShowNamed(String label) {
    return 'Mostrar $label';
  }

  @override
  String kitFieldHideNamed(String label) {
    return 'Ocultar $label';
  }

  @override
  String get kitFieldPaste => 'Pegar';

  @override
  String get kitFieldSaved => 'Guardado';

  @override
  String get kitFieldReplace => 'Reemplazar';

  @override
  String get kitFieldChecking => 'Comprobando…';

  @override
  String kitFieldStillChecking(int seconds) {
    return 'Aún comprobando tras $seconds s';
  }

  @override
  String kitFieldCount(int count, int max) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);
    final intl.NumberFormat maxNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String maxString = maxNumberFormat.format(max);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString de $maxString',
      one: '1 de $maxString',
    );
    return '$_temp0';
  }

  @override
  String get kitFieldLimitReached => 'Límite alcanzado';

  @override
  String get kitFieldErrorLabel => 'Error';

  @override
  String get kitTappableShowActions => 'Mostrar acciones';

  @override
  String kitWorkRead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos',
      one: '1 archivo',
    );
    return 'leyó $_temp0';
  }

  @override
  String kitWorkSearched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count veces',
      one: 'una vez',
    );
    return 'buscó $_temp0';
  }

  @override
  String kitWorkListed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count carpetas',
      one: '1 carpeta',
    );
    return 'listó $_temp0';
  }

  @override
  String kitWorkEdited(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos',
      one: '1 archivo',
    );
    return 'editó $_temp0';
  }

  @override
  String kitWorkRan(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count comandos',
      one: '1 comando',
    );
    return 'ejecutó $_temp0';
  }

  @override
  String kitWorkFetched(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count páginas',
      one: '1 página',
    );
    return 'obtuvo $_temp0';
  }

  @override
  String kitWorkDelegated(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tareas',
      one: '1 tarea',
    );
    return 'delegó $_temp0';
  }

  @override
  String kitWorkOther(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pasos más',
      one: '1 paso más',
    );
    return '$_temp0';
  }

  @override
  String kitWorkNotRun(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count no se ejecutaron',
      one: '1 no se ejecutó',
    );
    return '$_temp0';
  }

  @override
  String kitWorkSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pasos',
      one: '1 paso',
    );
    return '$_temp0';
  }

  @override
  String get kitWorkSeparator => ' · ';

  @override
  String get kitWorkWaitingForYou => 'Esperándote';

  @override
  String get kitWorkStopped => 'Detenido';

  @override
  String get kitWorkDidntFinish => 'No terminó';

  @override
  String get kitWorkWorking => 'Trabajando';

  @override
  String kitWorkEarlierSteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pasos anteriores',
      one: '1 paso anterior',
    );
    return 'Mostrar $_temp0';
  }

  @override
  String get kitReceiptSending => 'Enviando…';

  @override
  String get kitReceiptSent => 'Enviado';

  @override
  String get kitReceiptConfirmed => 'Hecho';

  @override
  String get kitReceiptNotConfirmed => 'Aún sin confirmar';

  @override
  String get kitReceiptRefused => 'No aceptado';

  @override
  String kitReceiptRefusedReason(String reason) {
    return 'No aceptado: $reason';
  }

  @override
  String kitReceiptAnsweredElsewhere(String where) {
    return 'Respondido en $where';
  }

  @override
  String get kitReceiptAnsweredElsewhereUnknown =>
      'Respondido en otro dispositivo';

  @override
  String kitReceiptActRefusedReason(String act, String reason) {
    return '$act: $reason';
  }

  @override
  String kitReceiptAt(String time) {
    return 'a las $time';
  }

  @override
  String get kitDetailsHide => 'Ocultar detalles';

  @override
  String get kitCopyAll => 'Copiar todo';

  @override
  String kitCopyValue(String label) {
    return 'Copiar $label';
  }

  @override
  String kitDetailsShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar las $count líneas',
    );
    return '$_temp0';
  }

  @override
  String kitDetailsValueSpoken(String label, String value) {
    return '$label: $value';
  }

  @override
  String get kitProgressRowLoading => 'Cargando';

  @override
  String kitProgressRowPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString por ciento';
  }

  @override
  String get kitProgressRowNearLimit => 'Cerca del límite';

  @override
  String get kitProgressRowAtLimit => 'Límite alcanzado';

  @override
  String kitProgressRowAsOf(String time) {
    return 'a las $time';
  }

  @override
  String get kitProgressRowOther => 'Otros';

  @override
  String get kitModelServerDefault => 'Predeterminado del servidor';

  @override
  String get kitModelSignIn => 'Inicia sesión para usar modelos';

  @override
  String get kitModelChoose => 'Elegir un modelo';

  @override
  String get kitModelChange => 'Cambiar de modelo';

  @override
  String get kitModelActions => 'Atajos de modelo';

  @override
  String kitModelContext(String percent) {
    return '$percent %';
  }

  @override
  String kitAttachmentOpen(String label) {
    return 'Vista previa de $label';
  }

  @override
  String kitAttachmentImage(String label) {
    return 'Imagen, $label';
  }

  @override
  String kitAttachmentFile(String label) {
    return 'Archivo, $label';
  }

  @override
  String kitAttachmentFolder(String label) {
    return 'Carpeta, $label';
  }

  @override
  String kitAttachmentReference(String label) {
    return 'Referencia, $label';
  }

  @override
  String get kitSuggestionsShowAll => 'Mostrar todo';

  @override
  String get kitSuggestionsLabel => 'Sugerencias';

  @override
  String get kitNeedsYouReasonDecision => 'Necesita tu decisión';

  @override
  String get kitNeedsYouReasonBlocked => 'Bloqueado: te necesita';

  @override
  String get kitNeedsYouReasonConsent => 'Necesita tu visto bueno';

  @override
  String kitNeedsYouSpan(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString te necesitan · ',
      one: 'Te necesita · ',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouBadgeSuffix(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: ', $countString te necesitan',
      one: ', 1 te necesita',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWaiting(String age) {
    return 'esperando $age';
  }

  @override
  String kitNeedsYouWaitingSpoken(int minutes) {
    final intl.NumberFormat minutesNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String minutesString = minutesNumberFormat.format(minutes);

    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'esperando $minutesString minutos',
      one: 'esperando 1 minuto',
      zero: 'esperando menos de un minuto',
    );
    return '$_temp0';
  }

  @override
  String kitNeedsYouWhoOnServer(String who, String server) {
    return '$who en $server';
  }

  @override
  String get kitWorkGraph => 'Grafo de trabajo';

  @override
  String get kitWorkGraphEmpty => 'Aún no hay elementos de trabajo';

  @override
  String kitWorkGraphNode(String title, String state) {
    return '$title, $state';
  }

  @override
  String kitWorkGraphNeeds(String title) {
    return 'necesita $title';
  }

  @override
  String kitWorkGraphNeedsMore(String title, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count',
      one: '1',
    );
    return 'necesita $title y $_temp0 más';
  }

  @override
  String get kitJumpLatest => 'Ir a lo último';

  @override
  String kitJumpNewLatest(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count nuevos · Ir a lo último',
      one: '1 nuevo · Ir a lo último',
    );
    return '$_temp0';
  }

  @override
  String get kitChoiceCurrent => 'Actual';

  @override
  String get kitChoiceRecommended => 'Recomendado';

  @override
  String get kitChoiceOtherSend => 'Enviar respuesta';

  @override
  String kitChoiceSelectedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count seleccionados',
      one: '1 seleccionado',
      zero: 'Ninguno seleccionado',
    );
    return '$_temp0';
  }

  @override
  String get kitComposerField => 'Mensaje';

  @override
  String get kitComposerSend => 'Enviar';

  @override
  String get kitComposerSending => 'Enviando';

  @override
  String get kitComposerSendOffline => 'Enviar al reconectar';

  @override
  String get kitComposerSendAfter => 'Enviar tras esta respuesta';

  @override
  String get kitComposerAddToTurn => 'Añadir a la respuesta en curso';

  @override
  String get kitComposerStop => 'Detener la respuesta';

  @override
  String get kitComposerSendsAfter => 'Se envía tras esta respuesta';

  @override
  String get kitComposerStopAndSend => 'Detener y enviar';

  @override
  String get kitComposerStopAndSendNote =>
      'Detiene la respuesta actual y vuelve a empezar con tu mensaje.';

  @override
  String get kitComposerCannotSendYet =>
      'Podrás enviar cuando termine esta respuesta';

  @override
  String get kitComposerTools => 'Adjuntar y más';

  @override
  String get kitComposerVoice => 'Hablar en vez de escribir';

  @override
  String get kitComposerEditor => 'Abrir el editor a pantalla completa';

  @override
  String get kitVoiceLeave => 'Salir del modo de voz';

  @override
  String get kitVoiceStarting => 'Preparando el micrófono…';

  @override
  String get kitVoiceListening => 'Escuchando…';

  @override
  String get kitVoiceTranscribing => 'Escribiendo lo que dijiste…';

  @override
  String get kitVoiceWaitingReply => 'Esperando la respuesta…';

  @override
  String get kitVoiceSpeaking => 'Leyendo la respuesta en voz alta';

  @override
  String get kitVoiceReplyReady => 'La respuesta está lista';

  @override
  String get kitVoicePaused => 'En pausa · el agente te necesita';

  @override
  String get kitVoiceMicDenied => 'El micrófono está desactivado para esta app';

  @override
  String get kitVoiceFailed => 'La voz se detuvo';

  @override
  String get kitVoiceSend => 'Enviar';

  @override
  String get kitVoiceDone => 'Listo';

  @override
  String get kitVoiceStopReading => 'Dejar de leer';

  @override
  String get kitVoiceReadReply => 'Leerla en voz alta';

  @override
  String get kitVoiceListen => 'Escuchar';

  @override
  String get kitVoiceReadAloud => 'Leer las respuestas en voz alta';

  @override
  String kitVoiceElapsed(String minutes, String seconds) {
    return '$minutes:$seconds';
  }

  @override
  String get kitSearchClear => 'Borrar búsqueda';

  @override
  String get kitSearchFilter => 'Filtrar';

  @override
  String kitSearchFilterActive(String name) {
    return 'Filtro: $name';
  }

  @override
  String kitSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count resultados',
      one: '1 resultado',
      zero: 'Sin resultados',
    );
    return '$_temp0';
  }

  @override
  String kitSearchPartial(int count) {
    return '$count cargados · buscando en el servidor…';
  }

  @override
  String kitSearchNoMatch(String query) {
    return 'Nada coincide con $query';
  }

  @override
  String kitSearchNoMatchIn(String what, String query) {
    return 'Nada en $what coincide con $query';
  }

  @override
  String get kitTopBarBack => 'Atrás';

  @override
  String get kitErrorFallbackMessage =>
      'Esta parte de OpenCode chocó con un error. Vuelve atrás y reintenta, o abre Diagnóstico de la app para ver los detalles.';

  @override
  String get kitTopBarClose => 'Cerrar';

  @override
  String get kitTopBarSearch => 'Buscar';

  @override
  String get kitTopBarSwitchServer => 'Cambiar de servidor';

  @override
  String get kitTopBarSwitchProject => 'Cambiar de proyecto';

  @override
  String get kitTopBarMore => 'Más acciones';

  @override
  String get kitAgentStripLabel => 'Agentes en esta tarea';

  @override
  String kitAgentOpen(String name) {
    return 'Abrir la conversación de $name';
  }

  @override
  String kitAgentLabel(String hasRole, String name, String role, String state) {
    String _temp0 = intl.Intl.selectLogic(hasRole, {
      'yes': '$name, $role, $state',
      'other': '$name, $state',
    });
    return '$_temp0';
  }

  @override
  String get kitBreadcrumb => 'Ruta de la carpeta';

  @override
  String kitBreadcrumbOpen(String folder) {
    return 'Abrir la carpeta $folder';
  }

  @override
  String kitBreadcrumbOpenRoot(String root) {
    return 'Abrir $root';
  }

  @override
  String kitBreadcrumbCurrent(String folder) {
    return 'Carpeta actual: $folder';
  }

  @override
  String kitBreadcrumbMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count carpetas más',
      one: '1 carpeta más',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeCopyCode => 'Copiar el código';

  @override
  String get kitCodeCopyCommand => 'Copiar el comando';

  @override
  String get kitCodeCopyOutput => 'Copiar la salida';

  @override
  String get kitCodeCopyFailedCode => 'No se pudo copiar el código. Reintenta.';

  @override
  String get kitCodeCopyFailedCommand =>
      'No se pudo copiar el comando. Reintenta.';

  @override
  String get kitCodeCopyFailedOutput =>
      'No se pudo copiar la salida. Reintenta.';

  @override
  String kitCodeShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar las $count líneas',
    );
    return '$_temp0';
  }

  @override
  String get kitCodeOpenFull => 'Abrir la salida completa';

  @override
  String get kitWrapLines => 'Ajustar líneas';

  @override
  String kitCodeChanges(int added, int removed) {
    return '$added agregadas, $removed quitadas';
  }

  @override
  String get kitCodeEmpty => 'Vacío';

  @override
  String kitTabLabel(String label, int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$label, $countString';
  }

  @override
  String kitQueuedTitle(int count) {
    return 'En espera de envío · $count';
  }

  @override
  String get kitQueuedOffline => 'Se envía cuando vuelvas a estar en línea';

  @override
  String get kitQueuedWaiting => 'En espera de envío';

  @override
  String get kitQueuedReachedServer => 'Llegó al servidor';

  @override
  String get kitQueuedAfterReply => 'Se envía después de esta respuesta';

  @override
  String get kitQueuedAddToTurn => 'Se agrega a este turno';

  @override
  String get kitQueuedUpdate => 'Actualización en espera';

  @override
  String kitQueuedAttachments(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count adjuntos',
      one: '1 adjunto',
    );
    return '$_temp0';
  }

  @override
  String kitQueuedItemLabel(int index, int count, String text, String state) {
    return 'Mensaje en espera $index de $count: $text. $state';
  }

  @override
  String get kitQueuedActions => 'Acciones del mensaje';

  @override
  String get kitLogTitle => 'Salida';

  @override
  String get kitLogShowOutput => 'Mostrar la salida';

  @override
  String get kitLogLive => 'En vivo';

  @override
  String kitLogQuiet(String age) {
    return 'Última línea hace $age';
  }

  @override
  String kitLogQuietSeconds(int seconds) {
    return 'Última línea hace $seconds s';
  }

  @override
  String get kitLogEnded => 'Terminó';

  @override
  String kitLogEndedExit(String code) {
    return 'Terminó · salida $code';
  }

  @override
  String get kitLogFailed => 'Falló';

  @override
  String kitLogFailedExit(String code) {
    return 'Falló · salida $code';
  }

  @override
  String get kitLogEmpty => 'Aún sin salida';

  @override
  String kitLogNewLines(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas nuevas',
      one: '1 línea nueva',
    );
    return '$_temp0';
  }

  @override
  String kitLogDropped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas anteriores no mostradas',
      one: '1 línea anterior no mostrada',
    );
    return '$_temp0';
  }

  @override
  String get kitLogReadFailed => 'No se pudo leer la salida';

  @override
  String kitLogWarningLine(String line) {
    return 'Aviso: $line';
  }

  @override
  String kitLogErrorLine(String line) {
    return 'Error: $line';
  }

  @override
  String get kitUntilOff => 'Hasta que lo desactive';

  @override
  String get kitUntilConversation => 'Durante esta conversación';

  @override
  String get kitUntilHour => 'Durante una hora';

  @override
  String get kitRiskTurnOn => 'Activar';

  @override
  String get kitRiskNotNow => 'Ahora no';

  @override
  String get kitRiskTurnOff => 'Desactivar';

  @override
  String get safetyDisconnectBody =>
      'Las actualizaciones en vivo se detienen y vuelves a la lista de servidores. El servidor sigue en marcha y nada en él cambia.';

  @override
  String get safetyDisconnectBodyPhone =>
      'Las actualizaciones en vivo se detienen y vuelves a la lista de servidores. OpenCode sigue ejecutándose en este teléfono, gastando batería, hasta que lo detengas.';

  @override
  String safetyDisconnectWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count mensajes en espera de envío se quedan en este teléfono hasta que vuelvas a conectar.',
      one:
          '1 mensaje en espera de envío se queda en este teléfono hasta que vuelvas a conectar.',
    );
    return '$_temp0';
  }

  @override
  String get folderBrowserSlowTitle => 'Aún leyendo esta carpeta';

  @override
  String get folderBrowserSlowBody =>
      'Listar las carpetas de este teléfono puede tardar hasta 15 segundos.';

  @override
  String get folderBrowserFirstProject => 'Nombra tu primer proyecto';

  @override
  String kitChecklistNext(String step) {
    return 'siguiente: $step';
  }

  @override
  String kitChecklistNeedsYou(String action) {
    return 'requiere tu atención, $action';
  }

  @override
  String get kitChecklistShowSteps => 'Mostrar los pasos';

  @override
  String get kitChecklistHideSteps => 'Ocultar los pasos';

  @override
  String get kitRequestAllowOnce => 'Permitir una vez';

  @override
  String get kitRequestReject => 'Rechazar';

  @override
  String get kitRequestApprove => 'Aprobar';

  @override
  String get kitRequestSendBack => 'Devolver';

  @override
  String get kitRequestAnswer => 'Responder';

  @override
  String get kitRequestSend => 'Enviar';

  @override
  String get kitRequestReplyEmptyReason => 'Escribe una respuesta primero.';

  @override
  String kitRequestMoreAnswers(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString respuestas más',
      one: '1 respuesta más',
    );
    return '$_temp0';
  }

  @override
  String get kitRequestExpired => 'Caducó · el agente dejó de esperar';

  @override
  String kitRequestAge(String age) {
    return 'esperando $age';
  }

  @override
  String kitDiffFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos',
      one: '1 archivo',
    );
    return '$_temp0';
  }

  @override
  String kitDiffChangeOf(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'Cambio $indexString de $countString';
  }

  @override
  String get kitDiffPreviousChange => 'Cambio anterior';

  @override
  String get kitDiffNextChange => 'Cambio siguiente';

  @override
  String kitDiffLines(int start, int end) {
    final intl.NumberFormat startNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String startString = startNumberFormat.format(start);
    final intl.NumberFormat endNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String endString = endNumberFormat.format(end);

    return 'Líneas $startString–$endString';
  }

  @override
  String get kitDiffHideUnchanged => 'Ocultar las líneas sin cambios';

  @override
  String get kitDiffNoChanges => 'No hay cambios';

  @override
  String get kitDiffBinary => 'Archivo binario · no se muestra';

  @override
  String kitDiffRenamed(String path) {
    return 'Renombrado desde $path';
  }

  @override
  String get kitDiffAddedFile => 'Archivo nuevo';

  @override
  String get kitDiffDeletedFile => 'Eliminado';

  @override
  String kitDiffTooBig(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return 'Se muestran $shownString de $totalString líneas';
  }

  @override
  String get kitDiffOpenAll => 'Abrir todos';

  @override
  String kitDiffLineAdded(int number) {
    return 'Línea $number agregada';
  }

  @override
  String kitDiffLineRemoved(int number) {
    return 'Línea $number eliminada';
  }

  @override
  String kitDiffLinesAdded(int start, int end) {
    return 'Líneas $start–$end agregadas';
  }

  @override
  String kitDiffLinesRemoved(int start, int end) {
    return 'Líneas $start–$end eliminadas';
  }

  @override
  String get kitDiffShowSource => 'Origen';

  @override
  String get kitDiffShowFormatted => 'Formateado';

  @override
  String get kitDiffComment => 'Comentar';

  @override
  String get kitDiffAddToPrompt => 'Agregar al mensaje';

  @override
  String get kitDiffCopyLines => 'Copiar las líneas';

  @override
  String get kitDiffClearSelection => 'Vaciar la selección';

  @override
  String kitDiffSelected(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas seleccionadas',
      one: '1 línea seleccionada',
    );
    return '$_temp0';
  }

  @override
  String kitDiffCounts(int added, int removed) {
    return '$added agregadas, $removed eliminadas';
  }

  @override
  String get kitDiffLoadFailed => 'No se pudieron cargar los cambios';

  @override
  String kitDiffLine(int number) {
    return 'Línea $number';
  }

  @override
  String kitBoardLane(String column, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tareas',
      one: '1 tarea',
      zero: 'sin tareas',
    );
    return '$column, $_temp0';
  }

  @override
  String kitBoardLaneLoading(String column) {
    return 'Cargando $column';
  }

  @override
  String get kitMarkdownOpenFile => 'Abrir el archivo';

  @override
  String kitMarkdownTable(int rows) {
    String _temp0 = intl.Intl.pluralLogic(
      rows,
      locale: localeName,
      other: 'Tabla, $rows filas',
      one: 'Tabla, 1 fila',
    );
    return '$_temp0';
  }

  @override
  String get kitToolNotRun => 'No se ejecutó';

  @override
  String get kitToolWaiting => 'En espera';

  @override
  String get kitToolRunning => 'En ejecución';

  @override
  String get kitToolWaitingForYou => 'Esperándote';

  @override
  String get kitToolDone => 'Listo';

  @override
  String get kitToolFailed => 'Falló';

  @override
  String get kitToolDeclined => 'Lo rechazaste';

  @override
  String get kitToolStopped => 'Detenido';

  @override
  String get kitToolBackground => 'Iniciado en segundo plano';

  @override
  String kitToolTookSeconds(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString segundos',
      one: '1 segundo',
    );
    return '$_temp0';
  }

  @override
  String kitToolTookMinutes(int count) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$countString minutos',
      one: '1 minuto',
    );
    return '$_temp0';
  }

  @override
  String get kitToolOpenConversation => 'Abrir su conversación';

  @override
  String get kitViewerFind => 'Buscar en el archivo';

  @override
  String kitViewerFindCount(int index, int count) {
    final intl.NumberFormat indexNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String indexString = indexNumberFormat.format(index);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return '$indexString de $countString';
  }

  @override
  String get kitViewerFindNone => 'Sin coincidencias';

  @override
  String get kitViewerFindPrevious => 'Coincidencia anterior';

  @override
  String get kitViewerFindNext => 'Coincidencia siguiente';

  @override
  String get kitViewerFindClose => 'Cerrar la búsqueda';

  @override
  String get kitViewerCopyContents => 'Copiar el contenido';

  @override
  String get kitViewerShowSource => 'Mostrar el origen';

  @override
  String get kitViewerEmpty => 'Este archivo está vacío';

  @override
  String kitViewerTruncated(int shown, int total) {
    final intl.NumberFormat shownNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String shownString = shownNumberFormat.format(shown);
    final intl.NumberFormat totalNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String totalString = totalNumberFormat.format(total);

    return 'Se muestran las primeras $shownString de $totalString líneas';
  }

  @override
  String get kitViewerPartial => 'Se muestra parte de este archivo';

  @override
  String get kitViewerOpenAll => 'Abrir todo';

  @override
  String get kitViewerCantShow => 'No se puede mostrar este archivo';

  @override
  String kitViewerCantShowBody(String type, String size) {
    return '$type · $size';
  }

  @override
  String get kitViewerUnknownType => 'Tipo desconocido';

  @override
  String get kitViewerUnknownSize => 'tamaño desconocido';

  @override
  String kitViewerLoadFailed(String name) {
    return 'No se pudo abrir $name';
  }

  @override
  String kitViewerPage(int page, int count) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    return 'Página $pageString de $countString';
  }

  @override
  String kitViewerPageFailed(int page) {
    final intl.NumberFormat pageNumberFormat = intl.NumberFormat.decimalPattern(
      localeName,
    );
    final String pageString = pageNumberFormat.format(page);

    return 'No se pudo mostrar la página $pageString';
  }

  @override
  String get kitCapServerAnyTitle => 'Servidor donde trabajar';

  @override
  String get kitCapServerAnyWhy =>
      'Aún no hay servidor. Configura uno en este teléfono o conecta una computadora.';

  @override
  String get kitCapServerAnyEnable => 'Agregar un servidor';

  @override
  String get kitCapServerAnyOffer =>
      'Agrega un servidor para empezar a trabajar con un agente.';

  @override
  String get kitCapServerOc2Title => 'Servidor de OpenCode 2';

  @override
  String get kitCapServerOc2Why => 'Necesita un servidor que corra OpenCode 2.';

  @override
  String get kitCapServerOc2Enable => 'Conectar OpenCode 2';

  @override
  String get kitCapServerOc2Offer =>
      'Esto necesita OpenCode 2. ¿Cambiar el servidor de este teléfono a él?';

  @override
  String get kitCapServerCodexTitle => 'Servidor de Codex';

  @override
  String get kitCapServerCodexWhy =>
      'Necesita una computadora que corra Codex.';

  @override
  String get kitCapServerCodexEnable => 'Conectar Codex';

  @override
  String get kitCapServerCodexOffer =>
      'Conecta una computadora que corra Codex para usarlo aquí.';

  @override
  String get kitCapServerPaseoTitle => 'Claude Code o Pi';

  @override
  String get kitCapServerPaseoWhy =>
      'Necesita Paseo, en una computadora o en este teléfono.';

  @override
  String get kitCapServerPaseoEnable => 'Conectar Paseo';

  @override
  String get kitCapServerPaseoOffer =>
      'Trabajar con Claude Code o Pi. ¿Conectar Paseo?';

  @override
  String get kitCapPhoneBuiltinTitle => 'Servidor en este teléfono';

  @override
  String get kitCapPhoneBuiltinWhy =>
      'Este teléfono aún no tiene un servidor propio.';

  @override
  String get kitCapPhoneBuiltinEnable => 'Configurar este teléfono';

  @override
  String get kitCapPhoneBuiltinOffer =>
      'Hacer correr agentes en este teléfono mismo. ¿Configurarlo?';

  @override
  String get kitCapPhoneAnyTitle => 'Servidor en este teléfono';

  @override
  String get kitCapPhoneAnyWhy =>
      'Necesita un servidor que corra en este teléfono.';

  @override
  String get kitCapModelAuthTitle => 'Inicio de sesión de modelo';

  @override
  String get kitCapModelAuthWhy =>
      'Inicia sesión en un proveedor de modelos para que el agente pueda responder.';

  @override
  String get kitCapModelAuthEnable => 'Iniciar sesión en un modelo';

  @override
  String get kitCapModelAuthOffer =>
      'El agente necesita un modelo para responder. ¿Iniciar sesión en uno?';

  @override
  String get kitCapTeamOnTitle => 'AI Team';

  @override
  String get kitCapTeamOnWhy => 'AI Team está apagado en este servidor.';

  @override
  String get kitCapTeamOnEnable => 'Activar AI Team';

  @override
  String get kitCapTeamOnOffer =>
      'Este servidor también puede hacer correr un equipo de IA. ¿Activarlo?';

  @override
  String get kitCapTeamPhoneTitle => 'AI Team en este teléfono';

  @override
  String get kitCapTeamPhoneWhy =>
      'Corre solo en el servidor propio de este teléfono.';

  @override
  String get kitCapTeamControlTitle => 'Controles del equipo';

  @override
  String get kitCapTeamControlWhy =>
      'Responde esto en la computadora que corre el equipo.';

  @override
  String get kitCapTeamControlEnable => 'Ver cómo configurarlo';

  @override
  String get kitCapTeamControlOffer =>
      'Controlar el equipo desde aquí una vez configurada la computadora. ¿Ver cómo?';

  @override
  String get kitCapClaudeLocalTitle => 'Claude Code en este teléfono';

  @override
  String get kitCapClaudeLocalWhy =>
      'Necesita Linux integrado en este teléfono y una suscripción de Claude.';

  @override
  String get kitCapClaudeLocalEnable => 'Agregar Claude Code';

  @override
  String get kitCapClaudeLocalOffer =>
      '¿Agregar Claude Code a este teléfono? Necesita una suscripción de Claude.';

  @override
  String get kitCapVoiceModelTitle => 'Dictado por voz';

  @override
  String get kitCapVoiceModelWhy =>
      'Necesita un modelo de voz en este teléfono.';

  @override
  String get kitCapVoiceModelEnable => 'Descargar el modelo de voz';

  @override
  String get kitCapVoiceModelOffer =>
      'Escribir con la voz en este teléfono. ¿Descargar un modelo de voz?';

  @override
  String get kitCapMcpAnyTitle => 'Herramientas extra';

  @override
  String get kitCapMcpAnyWhy =>
      'Este servidor no puede agregar herramientas extra desde la app.';

  @override
  String get kitCapMcpAnyEnable => 'Agregar una herramienta';

  @override
  String get kitCapMcpAnyOffer =>
      'Darle más herramientas al agente. ¿Agregar una?';

  @override
  String get kitCapProjectOpenTitle => 'Proyecto';

  @override
  String get kitCapProjectOpenWhy =>
      'Elige primero una carpeta donde trabajar.';

  @override
  String get kitCapProjectOpenEnable => 'Elegir un proyecto';

  @override
  String get kitCapProjectOpenOffer =>
      'Elige una carpeta de proyecto para empezar a trabajar.';

  @override
  String get kitCapProjectGitTitle => 'Proyecto de Git';

  @override
  String get kitCapProjectGitWhy =>
      'Esta carpeta aún no es un proyecto de Git.';

  @override
  String get kitCapProjectGitEnable => 'Convertir esto en un proyecto de Git';

  @override
  String get kitCapProjectGitOffer =>
      'Esto necesita un proyecto de Git. ¿Convertir esta carpeta en uno?';

  @override
  String get kitCapPermNotificationsTitle => 'Notificaciones';

  @override
  String get kitCapPermNotificationsWhy =>
      'Las notificaciones están apagadas para esta app.';

  @override
  String get kitCapPermNotificationsEnable => 'Permitir notificaciones';

  @override
  String get kitCapPermNotificationsOffer =>
      'Enterarte cuando un agente te requiera o termine. ¿Permitir notificaciones?';

  @override
  String get kitCapPermBatteryTitle => 'Funcionamiento en el fondo';

  @override
  String get kitCapPermBatteryWhy =>
      'Android puede detener la app mientras está en el fondo.';

  @override
  String get kitCapPermBatteryEnable =>
      'Permitir el funcionamiento en el fondo';

  @override
  String get kitCapPermBatteryOffer =>
      '¿Mantener los agentes corriendo cuando la app esté cerrada?';

  @override
  String get kitCapPermCameraTitle => 'Cámara';

  @override
  String get kitCapPermCameraWhy =>
      'El acceso a la cámara está apagado para esta app.';

  @override
  String get kitCapPermCameraEnable => 'Permitir la cámara';

  @override
  String get kitCapPermCameraOffer =>
      'Escanear códigos de emparejamiento y agregar fotos. ¿Permitir la cámara?';

  @override
  String get kitCapPermMicTitle => 'Micrófono';

  @override
  String get kitCapPermMicWhy =>
      'El acceso al micrófono está apagado para esta app.';

  @override
  String get kitCapPermMicEnable => 'Permitir el micrófono';

  @override
  String get kitCapPermMicOffer =>
      'Decir tus mensajes en voz alta. ¿Permitir el micrófono?';

  @override
  String get kitCapNetworkTailscaleTitle => 'Alcanzable desde cualquier parte';

  @override
  String get kitCapNetworkTailscaleWhy =>
      'Tu teléfono y tu computadora no están en la misma red.';

  @override
  String get kitCapNetworkTailscaleEnable => 'Configurar Tailscale';

  @override
  String get kitCapNetworkTailscaleOffer =>
      'Alcanza tu computadora desde cualquier parte con Tailscale. ¿Configurarlo?';

  @override
  String get kitCapQuotaCollectorTitle => 'Consumo restante';

  @override
  String get kitCapQuotaCollectorWhy =>
      'Este servidor no reporta lo que te queda de tu plan.';

  @override
  String get kitCapQuotaCollectorEnable => 'Ver cómo agregarlo';

  @override
  String get kitCapQuotaCollectorOffer =>
      'Ver aquí lo que te queda de tu plan. ¿Agregarlo en el servidor?';

  @override
  String get kitCapAgentA2aTitle => 'Otros agentes';

  @override
  String get kitCapAgentA2aWhy => 'Aún no se agregan otros agentes.';

  @override
  String get kitCapAgentA2aEnable => 'Agregar un agente';

  @override
  String get kitCapAgentA2aOffer =>
      'Trabajar con agentes de otras apps. ¿Agregar uno?';

  @override
  String get kitCapFlagFileBrowsingTerminalTitle => 'Archivos y terminal';

  @override
  String get kitCapFlagFileBrowsingTerminalWhy =>
      'Este servidor no comparte sus archivos ni su terminal.';

  @override
  String get kitCapFlagSessionDiffTitle => 'Revisar cambios';

  @override
  String get kitCapFlagSessionDiffWhy =>
      'Este servidor no muestra los cambios que hizo un agente.';

  @override
  String get kitCapFlagServerCatalogTitle => 'Ajustes del servidor';

  @override
  String get kitCapFlagServerCatalogWhy =>
      'Este servidor no comparte sus proveedores, herramientas ni comandos.';

  @override
  String get kitCapFlagUsageStatisticsTitle => 'Gastos';

  @override
  String get kitCapFlagUsageStatisticsWhy =>
      'Este servidor no reporta lo que se gastó.';

  @override
  String get kitCapFlagStagedRevertSessionNotesTitle =>
      'Notas y deshacer paso a paso';

  @override
  String get kitCapFlagStagedRevertSessionNotesWhy =>
      'Este servidor no puede tomar notas para el agente ni deshacer paso a paso.';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesTitle =>
      'Tareas aparte y compartición';

  @override
  String get kitCapFlagWorktreeCreateSessionShareManagedWorkspacesWhy =>
      'Este servidor no puede hacer correr tareas aparte ni compartir conversaciones.';

  @override
  String get kitCapFlagDevelopmentServicesTitle => 'Servicios de desarrollo';

  @override
  String get kitCapFlagDevelopmentServicesWhy =>
      'Este servidor no puede iniciar ni detener servicios de desarrollo.';

  @override
  String get kitCapFlagRemoteUpgradeTitle => 'Actualizar el servidor';

  @override
  String get kitCapFlagRemoteUpgradeWhy =>
      'Este servidor no se puede actualizar desde la app.';

  @override
  String get kitCapFlagPromptAttachmentsTitle => 'Adjuntar fotos y archivos';

  @override
  String get kitCapFlagPromptAttachmentsWhy =>
      'Este servidor no puede tomar fotos ni archivos con un mensaje.';

  @override
  String get kitHostThisPhone => 'este teléfono';

  @override
  String get kitHostOpenCode2 => 'computadoras con OpenCode 2';

  @override
  String get kitHostCodex => 'Codex';

  @override
  String get kitHostPaseo => 'Paseo';

  @override
  String get kitHostDemo => 'el demo sin conexión';

  @override
  String get kitHostOpenCode => 'computadoras con OpenCode';

  @override
  String kitCapNotOnHost(int count, String feature, String host) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$feature no están disponibles en $host',
      one: '$feature no está disponible en $host',
    );
    return '$_temp0';
  }

  @override
  String kitCapWorksOn(String hosts) {
    return 'Funciona en $hosts';
  }

  @override
  String kitCapWhyElsewhere(String notHere, String worksOn) {
    return '$notHere. $worksOn.';
  }

  @override
  String kitCapAnd(String first, String last) {
    return '$first y $last';
  }

  @override
  String kitCapComma(String first, String next) {
    return '$first, $next';
  }

  @override
  String kitCapServerOnHost(String server, String host) {
    return '$server ($host)';
  }

  @override
  String get kitCapNotNow => 'Ahora no';

  @override
  String get desktopDropHint => 'Suelta para adjuntar';

  @override
  String get searchClaudeCodeGateTitle => 'No en este dispositivo';

  @override
  String get searchClaudeCodeGateDevice =>
      'Claude Code corre en un teléfono solo dentro del Linux integrado de la aplicación, que este dispositivo no tiene. Hazlo correr en una computadora con Paseo y agrega esa computadora como servidor.';

  @override
  String get searchClaudeCodeGateDesktop =>
      'Claude Code en este teléfono es para teléfonos Android con Linux integrado. En una computadora, corre Claude Code con Paseo y agrégalo como servidor.';

  @override
  String get searchClaudeCodeGateServers => 'Abrir servidores';

  @override
  String get activitySendOffline => 'Reconéctate al servidor para responder.';

  @override
  String get activityAnswerEveryQuestion =>
      'Responde todas las preguntas primero.';

  @override
  String get activityQuestionOptional => 'Opcional. Puedes dejar esta vacía.';

  @override
  String get activitySending => 'Enviando…';

  @override
  String activityQuestionProgress(int index, int total) {
    return 'Pregunta $index de $total';
  }

  @override
  String get activityOwnAnswer => 'O escribe tu propia respuesta';

  @override
  String get shortcutsPaletteSearch => 'Buscar o ir a…';

  @override
  String get shortcutsHelpAnywhere => 'En cualquier parte';

  @override
  String get shortcutsHelpConversation => 'En una conversación';

  @override
  String get homeShellProjectUnavailable => 'Archivos no está disponible';

  @override
  String homeShellProjectUnavailableReason(String server) {
    return '$server no ofrece archivos, cambios ni búsqueda de código. Conéctate a un servidor OpenCode para usarlos.';
  }

  @override
  String homeShellProjectUnavailableShort(String server) {
    return '$server no tiene herramientas de proyecto.';
  }

  @override
  String get workspaceContextFolder => 'Carpeta';

  @override
  String get workspaceSessionSharedLink => 'Enlace compartido';

  @override
  String get managedWorkspacesRefresh => 'Actualizar';

  @override
  String get managedWorkspacesDiscovered => 'El descubrimiento terminó';

  @override
  String get managedWorkspacesDiscoverFailed =>
      'No se pudieron descubrir los entornos';

  @override
  String get managedWorkspacesCreateFailed => 'No se pudo crear el entorno';

  @override
  String managedWorkspacesOpenFailed(String name) {
    return 'No se pudo abrir $name';
  }

  @override
  String managedWorkspacesRemoveTitle(String name) {
    return '¿Quitar $name?';
  }

  @override
  String managedWorkspacesRemoveBody(String provider) {
    return 'El servidor le pide a $provider eliminar este entorno y lo que hay en él.';
  }

  @override
  String get managedWorkspacesRemoveLeavesFirst =>
      'Está abierto ahora, así que la app vuelve primero a la carpeta del proyecto.';

  @override
  String get managedWorkspacesRemoveHistoryStays =>
      'Las conversaciones se quedan en el historial pero ya no pueden abrirlo.';

  @override
  String get managedWorkspacesRemoveAction => 'Quitar';

  @override
  String managedWorkspacesRemoved(String name) {
    return '$name se quitó';
  }

  @override
  String get managedWorkspacesProvider => 'Proveedor';

  @override
  String get managedWorkspacesProvidersFailed =>
      'No se pudieron cargar los proveedores';

  @override
  String get managedWorkspacesNoProviderTitle => 'Sin proveedor configurado';

  @override
  String get managedWorkspacesNoProviderBody =>
      'Este servidor no tiene proveedor de entornos en la nube. Agrega uno en la configuración de OpenCode del servidor y actualiza.';

  @override
  String managedWorkspacesEmptyBody(String project) {
    return 'Los entornos para $project aparecen aquí. Crea uno, o descubre los que un proveedor ya tenga.';
  }

  @override
  String get managedWorkspacesLoadFailed =>
      'No se pudieron cargar los entornos en la nube';

  @override
  String get managedWorkspacesCreating => 'Creando un entorno en la nube';

  @override
  String get managedWorkspacesCreatingBody =>
      'Esto normalmente toma unos minutos. Se abre aquí cuando esté listo.';

  @override
  String get managedWorkspacesCreateTakes =>
      'Crear uno normalmente toma unos minutos. Se abrirá aquí cuando esté listo.';

  @override
  String get managedWorkspacesBranchLabel => 'Rama';

  @override
  String get managedWorkspacesBranchHelper =>
      'Déjala vacía para usar la rama por defecto del proveedor.';

  @override
  String get managedWorkspacesInUse => 'En uso';

  @override
  String get managedWorkspacesCopyId => 'Copiar el ID';

  @override
  String get projectHealthGitInitSupporting =>
      'Corre git init aquí. No se compromete nada.';

  @override
  String get projectHealthSetUp => 'Configurar';

  @override
  String get projectHealthRunning => 'En marcha';

  @override
  String get projectHealthNotRunning => 'Detenido';

  @override
  String projectHealthLineCounts(int added, int removed) {
    return '$added líneas agregadas, $removed eliminadas';
  }

  @override
  String projectFolderCreateHelper(String directory) {
    return 'Creada en $directory de este teléfono y abierta como el proyecto.';
  }

  @override
  String get projectFolderMissingTitle => '¿Crear esta carpeta?';

  @override
  String get projectFolderCreateFailedTitle => 'No se pudo crear la carpeta';

  @override
  String get projectFolderOpenFailedTitle => 'No se pudo abrir la carpeta';

  @override
  String get projectsOneFolderTitle => 'El servidor usa una sola carpeta';

  @override
  String servicesStarted(String name) {
    return '$name inició';
  }

  @override
  String servicesRemoved(String name) {
    return '$name se quitó';
  }

  @override
  String servicesStopTitle(String name) {
    return '¿Detener $name?';
  }

  @override
  String servicesRestartTitle(String name) {
    return '¿Reiniciar $name?';
  }

  @override
  String servicesForgetTitle(String name) {
    return '¿Olvidar la última ejecución de $name?';
  }

  @override
  String servicesRemoveTitle(String name) {
    return '¿Quitar $name?';
  }

  @override
  String get servicesRemoveRunningHint =>
      'Su comando sigue corriendo en el servidor, y esta app ya no puede detenerlo. Deténlo primero para terminarlo.';

  @override
  String get servicesEmptyTitle => 'Aún no hay comandos de desarrollo';

  @override
  String get servicesOffline =>
      'El servidor no responde. Los comandos no se pueden iniciar ni revisar hasta que reconecte.';

  @override
  String get servicesLogFailed => 'No se pudo leer el log.';

  @override
  String get servicesProjectFolder => 'Carpeta del proyecto';

  @override
  String get servicesWorkspace => 'Entorno';

  @override
  String get servicesNameRequired => 'Escribe un nombre.';

  @override
  String get servicesDuplicateName => 'Ya existe un servicio con este nombre.';

  @override
  String get servicesCommandRequired => 'Escribe un comando, como npm run dev.';

  @override
  String get servicesUrlInvalid =>
      'Escribe una dirección http o https sin nombre de usuario ni contraseña.';

  @override
  String get isolatedTaskProjectFolder => 'Carpeta del proyecto';

  @override
  String get isolatedTaskStageCreate => 'Creando la copia';

  @override
  String get isolatedTaskStagePrepare =>
      'Corriendo la configuración del proyecto';

  @override
  String get isolatedTaskStageOpen => 'Abriendo la conversación';

  @override
  String get isolatedTaskUsually => 'Normalmente de 1 a 3 minutos';

  @override
  String get savedPermissionsIntro =>
      'Acciones que el agente puede tomar en este proyecto sin preguntarte antes. Revoca una y el agente pregunta otra vez.';

  @override
  String get savedPermissionsLoadFailed =>
      'No se pudieron cargar las acciones siempre permitidas';

  @override
  String get savedPermissionsRevokeBody =>
      'El agente te preguntará otra vez la siguiente vez que quiera hacer esto. El trabajo que ya está corriendo sigue.';

  @override
  String savedPermissionsRevokedDetail(String action) {
    return '$action ahora te pregunta primero otra vez.';
  }

  @override
  String get savedPermissionsDismiss => 'Omitir';

  @override
  String get savedPermissionsCopyPattern => 'Copiar el patrón';

  @override
  String get savedPermissionsBusy => 'Espera a que termine el cambio actual';

  @override
  String get savedPermissionsLoading =>
      'Cargando las acciones siempre permitidas';

  @override
  String get savedPermissionsAllResources =>
      'Cualquier cosa que este tipo de acción toque';

  @override
  String get settingsHubDetailEmpty => 'Elige un grupo de ajustes';

  @override
  String get notifyQuietStartPicker =>
      'Define cuándo empiezan las horas de silencio';

  @override
  String get notifyQuietEndPicker =>
      'Define cuándo terminan las horas de silencio';

  @override
  String get notifyQuietSet => 'Definir';

  @override
  String get notifyQuietAllDay =>
      'El inicio y el fin coinciden, así que las notificaciones permanecen en silencio todo el día.';

  @override
  String get notifySendingTest => 'Enviando una notificación de prueba…';

  @override
  String get notifyNoServersTitle => 'No hay servidores que vigilar';

  @override
  String get notifyNoServersDetail =>
      'Los servidores que guardes se pueden vigilar desde aquí, para que te llegue una solicitud de cualquiera de ellos.';

  @override
  String get notifyDismiss => 'Descartar';

  @override
  String get notifySaving => 'Guardando';

  @override
  String get notifyMonitorDetails =>
      'Cómo funciona la vigilancia de servidores';

  @override
  String get notifyRestartBackground => 'Reiniciar la conexión en vivo';

  @override
  String get privacySharedSection => 'Compartido con tu servidor';

  @override
  String get privacySaving => 'Guardando…';

  @override
  String get privacyDeleting => 'Eliminando';

  @override
  String privacyDeleteQueuedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Eliminar $count mensajes en cola',
      one: 'Eliminar 1 mensaje en cola',
      zero: 'Eliminar mensajes en cola',
    );
    return '$_temp0';
  }

  @override
  String privacyDeleteDraftsCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Eliminar $count borradores',
      one: 'Eliminar 1 borrador',
      zero: 'Eliminar borradores',
    );
    return '$_temp0';
  }

  @override
  String get kitMessageYou => 'Dijiste';

  @override
  String get kitMessageThinking => 'Pensando…';

  @override
  String get kitMessageThought => 'Pensó';

  @override
  String kitMessageThoughtForSeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count segundos',
      one: '1 segundo',
    );
    return 'Pensó durante $_temp0';
  }

  @override
  String kitMessageThoughtForMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count minutos',
      one: '1 minuto',
    );
    return 'Pensó durante $_temp0';
  }

  @override
  String get kitMessageActions => 'Acciones del mensaje';

  @override
  String get kitMessageNoticeFailed => 'Falló';

  @override
  String get kitRequestChooseOneReason => 'Elige al menos una respuesta.';

  @override
  String get kitRequestSendAnswers => 'Enviar respuestas';

  @override
  String get serversRemoveBody =>
      'Este teléfono olvida el servidor: su contraseña, el modelo y agente elegidos, y las conversaciones de proyectos y widgets.';

  @override
  String serversRemoveDrafts(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se eliminarán $count borradores sin enviar',
      one: 'Se eliminará 1 borrador sin enviar',
    );
    return '$_temp0';
  }

  @override
  String get serversRemoveActiveNext =>
      'Estás conectado a él: la app se desconecta y muestra tus servidores';

  @override
  String get serversRemoveServerKeeps =>
      'No se elimina nada en el servidor ni en tus proveedores de IA';

  @override
  String get guideStepTwoScan =>
      'Toca Agregar servidor, después Escanear código y apunta la cámara al QR, o Pega el código.';

  @override
  String get guideStepTwoPaste =>
      'Copia el código impreso y luego toca Agregar servidor y Pegar código.';

  @override
  String get guidePhonePathTitle => 'Usa este teléfono en su lugar';

  @override
  String get guidePhonePathBody =>
      'Instala OpenCode en este teléfono y úsalo aquí, sin computadora';

  @override
  String get pairingScannerAllowCamera => 'Permitir cámara';

  @override
  String get pairingScannerStarting => 'Abriendo la cámara…';

  @override
  String profileMonitorSwitchBody(String current, String target) {
    return 'Una ejecución está en marcha en $current. Al cambiar, $target se muestra en esta app; la ejecución en $current sigue.';
  }

  @override
  String get profileMonitorOpenFailedTitle => 'No se pudo abrir';

  @override
  String get profileMonitorIfIgnored => 'El agente espera hasta que respondas';

  @override
  String get serverSettingsRestartedIt => 'Lo reinicié';

  @override
  String serverSettingsUpgradeBody(
    String target,
    String server,
    String current,
  ) {
    return 'Instala OpenCode $target en $server (hoy $current) con el instalador propio del servidor.';
  }

  @override
  String serverSettingsUpgradeKeepsRunning(String current) {
    return 'El servidor sigue corriendo $current mientras instala';
  }

  @override
  String serverSettingsUpgradeRestartAfter(String target) {
    return 'Reiniciar el proceso de OpenCode en su computadora para usar $target';
  }

  @override
  String get serverSettingsUpgradeKeepsData =>
      'Los datos del servidor se quedan en su lugar';

  @override
  String serverSettingsCopyUpdateCommands(String server) {
    return 'Copiar los comandos de actualización de $server';
  }

  @override
  String get serverSettingsAddressLabel => 'Dirección';

  @override
  String get tailscaleSetupAppTitle => 'Tailscale en este teléfono';

  @override
  String get tailscaleSetupVpnTitle => 'Inicia sesión y conecta';

  @override
  String get tailscaleSetupVpnSupporting =>
      'Inicia sesión y conecta. OpenCode no puede comprobarlo.';

  @override
  String get tailscaleSetupOpenFailed =>
      'Tailscale no se abrió. Ábrelo desde tu launcher y vuelve.';

  @override
  String get tailscaleSetupAddressHelper =>
      'Pega la dirección HTTPS que mostró Tailscale Serve.';

  @override
  String get tailscaleSetupGetApp => 'Obtener Tailscale';

  @override
  String get tailscaleSetupContinueReason =>
      'Primero escribe la dirección de tu servidor.';

  @override
  String languagePickerPartlyTranslated(int percent) {
    return 'Traducido en parte ($percent %)';
  }

  @override
  String appearancePickerPreviewLabel(String name) {
    return 'Vista previa de $name';
  }

  @override
  String get appearancePickerPreviewIn => 'Vista previa en';

  @override
  String get appearancePickerInUse => 'En uso ahora';

  @override
  String appearancePickerThemeApplied(String name) {
    return 'Tema cambiado a $name';
  }

  @override
  String get serverSwitcherTitle => 'Servidores';

  @override
  String get serverSwitcherCurrentMenu => 'Acciones del servidor';

  @override
  String get formRendererSending => 'Enviando tus respuestas…';

  @override
  String get formRendererChoose => 'Elegir';

  @override
  String get formRendererChooseDate => 'Elegir una fecha';

  @override
  String get formRendererChooseDateTime => 'Elegir una fecha y una hora';

  @override
  String formRendererDateAndTime(String date, String time) {
    return '$date a las $time';
  }

  @override
  String get formRendererUseDate => 'Usar la fecha';

  @override
  String get filePreviewPdfIsolated =>
      'Las páginas PDF no se renderizan en esta vista aparte. Guarda el archivo para leerlo en una app de PDF.';

  @override
  String get filePreviewCopyOriginal => 'Copiar el archivo original';

  @override
  String get filePreviewOpenInFiles => 'Abrir en Archivos';

  @override
  String get filePreviewViewMode => 'Mostrar el archivo como';

  @override
  String get filePreviewAttachFailed => 'No se pudo adjuntar el archivo';

  @override
  String get filePreviewSaveFailed => 'No se pudo guardar el archivo';

  @override
  String get kitTurnStarting => 'Iniciando el modelo…';

  @override
  String kitTurnStillStarting(int seconds) {
    return 'Aún esperando al modelo · $seconds s';
  }

  @override
  String get kitTurnStopped => 'Detuviste esta respuesta.';

  @override
  String get kitTurnInterrupted =>
      'La conexión se cortó antes de que terminara esta respuesta.';

  @override
  String get kitTurnCopy => 'Copiar respuesta';

  @override
  String get kitTurnMore => 'Más acciones para esta respuesta';

  @override
  String get kitTurnActions => 'Acciones de la respuesta';

  @override
  String serverSettingsDisconnectTitle(String serverName) {
    return 'Desconectarse de $serverName';
  }

  @override
  String get kitScannerStarting => 'Abriendo la cámara…';

  @override
  String get kitScannerSlow => 'La cámara sigue abriéndose';

  @override
  String get kitScannerPaused => 'Cámara en pausa';

  @override
  String get kitScannerPreview => 'Vista de la cámara';

  @override
  String get kitDateSet => 'Establecer fecha';

  @override
  String get kitTimeSet => 'Establecer hora';

  @override
  String get kitDateTimeSet => 'Establecer';

  @override
  String get kitDateType => 'Escribir una fecha';

  @override
  String get kitDateCalendar => 'Mostrar calendario';

  @override
  String kitDateFormatHint(String example) {
    return 'p. ej. $example';
  }

  @override
  String get kitDateField => 'Fecha';

  @override
  String get kitDateInvalid => 'No es una fecha';

  @override
  String kitDateOutOfRange(String first, String last) {
    return 'Elige una fecha entre $first y $last';
  }

  @override
  String get kitTimeHour => 'Hora';

  @override
  String get kitTimeMinute => 'Minuto';

  @override
  String get kitTimePeriod => 'Mañana o tarde';

  @override
  String get kitTimeInvalid => 'No es una hora';

  @override
  String get kitDateTimeNotSet => 'Sin establecer';

  @override
  String kitDateTimeClear(String title) {
    return 'Borrar $title';
  }

  @override
  String get kitDateUnavailable => 'Ese día no se puede elegir';

  @override
  String get filesLoadingFolder => 'Abriendo carpeta…';

  @override
  String get filesSearching => 'Buscando…';

  @override
  String get filesShowHidden => 'Mostrar archivos ocultos';

  @override
  String get filesOnlyHidden =>
      'Esta carpeta solo tiene archivos y carpetas ocultos.';

  @override
  String get filesCopyName => 'Copiar nombre';

  @override
  String globalSessionsMoveTitle(String project) {
    return '¿Mover a $project?';
  }

  @override
  String globalSessionsMoveBody(String title, String from, String to) {
    return '“$title” se mueve de $from a $to por el sistema de sincronización del servidor.';
  }

  @override
  String get globalSessionsMoveWhileWorking =>
      'Está trabajando ahora. Moverlo puede interrumpir el paso actual.';

  @override
  String globalSessionsMoveBack(String project) {
    return 'Para regresarlo, abre $project y elige Continuar aquí en Todas las conversaciones.';
  }

  @override
  String get globalSessionsFilterLabel => 'Mostrar';

  @override
  String get globalSessionsFilterActive => 'Activas';

  @override
  String get globalSessionsArchivedNoMatchMessage =>
      'Ninguna conversación archivada tiene ese título. Prueba una búsqueda más corta.';

  @override
  String get globalSessionsArchivedEmptyTitle =>
      'No hay conversaciones archivadas';

  @override
  String get globalSessionsArchivedEmptyMessage =>
      'Las conversaciones que archives en Trabajo aparecen aquí.';

  @override
  String get globalSessionsShowActive => 'Mostrar las conversaciones activas';

  @override
  String globalSessionsProjectInUse(String project) {
    return '$project · En uso';
  }

  @override
  String get globalSessionsCopyFolder => 'Copiar la ruta de la carpeta';

  @override
  String get worktreesStartConversation => 'Conversación nueva aquí';

  @override
  String get worktreesCreateHelper =>
      'Se crea una rama y una carpeta aparte y se corren las tareas de arranque del proyecto. Los espacios se vuelven guiones.';

  @override
  String get worktreesFolder => 'Carpeta';

  @override
  String get worktreesMainCopy => 'Copia principal';

  @override
  String get worktreesCopyFolder => 'Copiar la ruta de la carpeta';

  @override
  String get worktreesLoadFailedTitle =>
      'No se pudieron cargar los árboles de trabajo';

  @override
  String get worktreesSetupFailedWord => 'La configuración falló';

  @override
  String get importNeedsFile => 'Elige primero un archivo JSON.';

  @override
  String get importNeedsDestination => 'Elige primero dónde importarlo.';

  @override
  String get importFileLabel => 'Archivo';

  @override
  String get importPreviewLabel => 'Conversación';

  @override
  String importMessages(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes',
      one: '1 mensaje',
    );
    return '$_temp0';
  }

  @override
  String get importNoDestinationsTitle => 'No hay dónde importar';

  @override
  String get importChangeDestinationShort => 'Cambiar';

  @override
  String get importConversationId => 'ID de conversación';

  @override
  String get importParentId => 'ID de conversación primaria';

  @override
  String get importFolder => 'Carpeta';

  @override
  String get importEnvironmentId => 'ID de entorno en la nube';

  @override
  String get phoneSetupCustomizeAllInstalled =>
      'Todas las herramientas opcionales ya están en este teléfono.';

  @override
  String get phoneSetupCustomizeIncluded => 'Obligatorio';

  @override
  String get termuxProcsCopyCommand => 'Copiar el comando';

  @override
  String get termuxProcsProcessId => 'ID del proceso';

  @override
  String get termuxProcsParentId => 'ID del proceso padre';

  @override
  String get termuxProcsNoRestart => 'No se puede volver a iniciar desde aquí.';

  @override
  String get teamMergeFailedNext =>
      'No se fusionó nada. Arregla lo que el host dice y reintenta, o revisa los cambios.';

  @override
  String get teamStartRunRefusedKept =>
      'Tu tarea sigue aquí. Edítala y envíala de nuevo.';

  @override
  String get transcriptTogglesReasoningOn =>
      'Cuando está activado, el razonamiento del modelo se abre bajo cada respuesta.';

  @override
  String get transcriptTogglesUsageOn =>
      'Cuando está activado, cada mensaje muestra su hora, tokens y costo.';

  @override
  String get transcriptTogglesScope =>
      'Esto aplica a todas las conversaciones de este dispositivo.';

  @override
  String get handoffSheetCopyCommand => 'Copiar el comando';

  @override
  String get handoffSheetReloadConversation => 'Reintentar';

  @override
  String get handoffSheetPhoneServerNote =>
      'Si el otro teléfono aún no tiene este servidor guardado, lo dice y ofrece abrir Servidores para que lo agregues.';

  @override
  String get modelPickerChooseFirst => 'Elige primero un modelo.';

  @override
  String get modelPickerThinking => 'Razonamiento';

  @override
  String get modelPickerAgentBuild => 'Edita archivos y ejecuta comandos';

  @override
  String get modelPickerAgentPlan => 'Lee y planifica; no cambia archivos';

  @override
  String modelPickerDetailsOutput(String count) {
    return 'Hasta $count tokens por respuesta';
  }

  @override
  String modelPickerDetailsPrice(String input, String output) {
    return '$input por millón de tokens leídos, $output por millón escritos';
  }

  @override
  String get modelPickerCanThink => 'Piensa antes de responder';

  @override
  String get modelPickerCanUseTools => 'Usa herramientas';

  @override
  String get modelPickerCanReadAttachments =>
      'Lee las imágenes y archivos que adjuntas';

  @override
  String get modelPickerCopyId => 'Copiar id del modelo';

  @override
  String get modelPickerInUse => 'En uso';

  @override
  String get modelPickerUnavailableReason =>
      'No disponible en este servidor ahora mismo.';

  @override
  String get modelPickerCollections => 'Qué modelos mostrar';

  @override
  String get modelPickerSignInTitle =>
      'Hace falta iniciar sesión en un proveedor';

  @override
  String get modelPickerSignInBody =>
      'Ningún proveedor de este servidor tiene modelos todavía. Inicia sesión en uno y vuelve para elegir un modelo.';

  @override
  String modelPickerShowMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar $count modelos más',
      one: 'Mostrar 1 modelo más',
    );
    return '$_temp0';
  }

  @override
  String get modelPickerAgentBuildName => 'Build';

  @override
  String get modelPickerAgentPlanName => 'Plan';

  @override
  String phoneServerCardDisconnect(String server) {
    return 'Desconectar de $server';
  }

  @override
  String get phoneServerCardStartOpenCode => 'Iniciar OpenCode';

  @override
  String get phoneServerCardStopOpenCode => 'Detener OpenCode en este teléfono';

  @override
  String get phoneServerCardShowServerLog => 'Mostrar registro del servidor';

  @override
  String get phoneServerCardOpenTerminal => 'Abrir terminal';

  @override
  String get phoneServerCardFailedTitle => 'No se pudo terminar';

  @override
  String get phoneServerRestartFailedTitle => 'El reinicio falló';

  @override
  String get setupTerminalTitle => 'Salida de la configuración';

  @override
  String get productStatesActionFailedTitle => 'No se pudo completar';

  @override
  String get productStatesSwitchServer => 'Cambiar de servidor';

  @override
  String get externalLinkBlockedTitle => 'Enlace bloqueado';

  @override
  String get externalLinkBlockedBody =>
      'Esta app solo abre enlaces https://, y enlaces http:// tras tu confirmación.';

  @override
  String externalLinkOpensHost(String host) {
    return 'Abre $host fuera de esta app.';
  }

  @override
  String get externalLinkDontOpen => 'No abrir';

  @override
  String get externalLinkCopy => 'Copiar enlace';

  @override
  String get externalLinkAddress => 'Dirección completa';

  @override
  String get externalLinkOpenFailedTitle => 'No se pudo abrir el enlace';

  @override
  String get runCommandReconnecting =>
      'OpenCode está reconectando. Reintenta en un momento.';

  @override
  String runCommandArgumentsHelper(String command) {
    return 'Texto que se pasa a /$command. Déjalo vacío si el comando no toma ninguno.';
  }

  @override
  String get runCommandRunsIn => 'Corre en';

  @override
  String runCommandFailedTitle(String command) {
    return 'No se pudo ejecutar /$command';
  }

  @override
  String get teamNowWakeRefusedNoReason => 'El host no dijo por qué.';

  @override
  String get teamHostFormTeamLabel => 'Nombre del equipo (opcional)';

  @override
  String get teamHostFormTeamHelper =>
      'Déjalo vacío para usar el equipo que corre la computadora.';

  @override
  String get teamHostFormHowAction => 'Cómo configurar la computadora';

  @override
  String get teamHostFormCancelTest => 'Cancelar la prueba';

  @override
  String get teamHostFormSaveAnyway => 'Guardar la dirección de todas formas';

  @override
  String get teamHostFormSaveAnywayNote =>
      'AI Team muestra el equipo como no responde hasta que la computadora responda.';

  @override
  String get teamHostFormConnectionDetails => 'Detalles de conexión';

  @override
  String teamAgentScreenPause(String agent) {
    return 'Pausar $agent';
  }

  @override
  String teamAgentScreenPaused(String agent) {
    return 'Pausado $agent';
  }

  @override
  String teamAgentScreenResume(String agent) {
    return 'Iniciar $agent otra vez';
  }

  @override
  String teamAgentScreenNudge(String agent) {
    return 'Darle un empujón a $agent';
  }

  @override
  String teamAgentScreenRestart(String agent) {
    return 'Reiniciar $agent';
  }

  @override
  String teamAgentScreenStop(String agent) {
    return 'Detener a $agent';
  }

  @override
  String teamAgentScreenStopBody(String agent, String task) {
    return '$agent deja de trabajar en “$task” ahora. La tarea se queda en el host, y puedes iniciar a $agent otra vez desde esta página.';
  }

  @override
  String teamAgentScreenStoppedTitle(String agent) {
    return '$agent está detenido';
  }

  @override
  String get teamAgentScreenStoppedBody =>
      'Su trabajo queda donde está. Inícialo otra vez cuando lo quieras de vuelta.';

  @override
  String teamAgentScreenCrashedTitle(String agent) {
    return '$agent se detuvo inesperadamente';
  }

  @override
  String get teamAgentScreenCrashedBody =>
      'Su sesión terminó por sí misma. Inícialo otra vez para retomar su trabajo desde el host.';

  @override
  String get teamAgentScreenRecyclingBody =>
      'Pronto empieza una sesión nueva y retoma su trabajo desde el host.';

  @override
  String teamAgentScreenModelFrom(String model, String provider) {
    return '$model de $provider';
  }

  @override
  String teamAgentScreenGateIfIgnored(String agent) {
    return '$agent espera hasta que respondas';
  }

  @override
  String teamAgentScreenControlsElsewhere(String agent) {
    return 'Este teléfono aún no puede pausar, detener ni escribirle a $agent en este host. Corre el frente del host del equipo en la computadora para controlarlo desde aquí.';
  }

  @override
  String get gateSheetDestructiveBody =>
      'El host marca esta acción como destructiva. Aprobarla no se puede deshacer desde el teléfono.';

  @override
  String get gateSheetAnswerLabel => 'Tu respuesta';

  @override
  String get gateSheetAfterAnswer =>
      'El equipo sigue en marcha apenas el host confirme tu respuesta.';

  @override
  String get gateSheetFixIt => 'Pedirle al equipo que lo arregle';

  @override
  String gateSheetFixItDetail(String agent) {
    return 'Le envía el error a $agent y le pide encontrar la causa y seguir.';
  }

  @override
  String gateSheetFixRequest(String task, String error) {
    return 'La tarea “$task” falló con este error:\n$error\nEncuentra la causa, arréglala y sigue en marcha.';
  }

  @override
  String gateSheetOpenAgent(String agent) {
    return 'Abrir la página de $agent';
  }

  @override
  String get teamIntroTurnOnPhone => 'Activar AI Team en este teléfono';

  @override
  String get teamIntroInstalledTitle => 'Instalado en este teléfono';

  @override
  String get teamIntroInstalledBody =>
      'Aún no está activado. Activarlo inicia el equipo para tu proyecto; nada más por descargar.';

  @override
  String get teamIntroSetUpPhone => 'Configurar AI Team en este teléfono';

  @override
  String teamIntroSetUpOn(String server) {
    return 'Configurar AI Team en $server';
  }

  @override
  String teamIntroTurnOn(String server) {
    return 'Activar AI Team en $server';
  }

  @override
  String get teamIntroCostTitle => 'Antes de configurarlo';

  @override
  String get teamIntroCostTime => 'Cerca de 8–10 minutos la primera vez';

  @override
  String get teamIntroCostMemory =>
      'Cerca de 550 MB de memoria por cada ejecutor';

  @override
  String get teamAgentScreenLabelId => 'ID del agente';

  @override
  String get gateSheetSendNeedsText => 'Escribe primero una respuesta';

  @override
  String teamAgentsChecked(String age) {
    return 'revisado hace $age';
  }

  @override
  String get teamWorkSheetMissingTitle => 'El elemento de trabajo desapareció';

  @override
  String get teamWorkSheetMissingBody =>
      'Puede que se haya terminado o quitado. Cierra esta hoja para ver la tarea como está ahora.';

  @override
  String get teamWorkSheetNotOnHost => 'Ya no está listado';

  @override
  String get teamWorkSheetOpenStepConversation =>
      'Abrir la conversación de este paso';

  @override
  String teamWorkSheetOpenAgentConversation(String name) {
    return 'Abrir la conversación de $name';
  }

  @override
  String get usageRangeLabel => 'Intervalo de fechas';

  @override
  String get usageAboutNumbers => 'Sobre estos números';

  @override
  String get usageBudgetHelperUsd =>
      'En dólares estadounidenses para este intervalo. Se te avisa cuando el informe lo alcance; nada se detiene.';

  @override
  String get usageBudgetHelperTokens =>
      'Tokens enteros para este intervalo. Se te avisa cuando el informe los alcance; nada se detiene.';

  @override
  String get usageBudgetClearConfirm => 'Borrar los presupuestos';

  @override
  String get usageBudgetNotSet => 'Sin fijar';

  @override
  String get usageBudgetWaitReason =>
      'Disponible cuando el consumo haya cargado.';

  @override
  String get usageBudgetUsdTitle => 'Presupuesto en USD';

  @override
  String get usageBudgetTokensTitle => 'Presupuesto de tokens';

  @override
  String get agentAccountScopeLostTitle => 'Este servidor cambió';

  @override
  String get agentAccountBackToServers => 'Volver a Servidores';

  @override
  String get agentAccountNotConnected =>
      'Conéctate a este servidor para ver su cuenta de Codex.';

  @override
  String get agentAccountSignInMethod => 'Inició sesión con';

  @override
  String get agentAccountPlanTitle => 'Plan';

  @override
  String get agentAccountCopyCode => 'Copiar el código de inicio de sesión';

  @override
  String agentAccountLimitReached(String reset) {
    return 'Alcanzaste un límite de Codex. $reset';
  }

  @override
  String get agentAccountResetDue => 'Se reajusta en cualquier momento';

  @override
  String agentAccountResetInDays(int days) {
    String _temp0 = intl.Intl.pluralLogic(
      days,
      locale: localeName,
      other: 'Se reajusta en $days días',
      one: 'Se reajusta en 1 día',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInHours(int hours) {
    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Se reajusta en $hours h',
      one: 'Se reajusta en 1 h',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetInMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'Se reajusta en $minutes min',
      one: 'Se reajusta en 1 min',
    );
    return '$_temp0';
  }

  @override
  String agentAccountResetWhen(String relative, String time) {
    return '$relative ($time)';
  }

  @override
  String get reviewWorkspaceScopes => 'Cambios por mostrar';

  @override
  String get reviewWorkspaceRefreshFailed =>
      'No se pudieron actualizar los cambios';

  @override
  String get reviewWorkspaceSlowTitle => 'Todavía leyendo los cambios';

  @override
  String get reviewWorkspaceSlowBody =>
      'El servidor corre git para comparar los archivos. Un proyecto grande puede tomar un minuto.';

  @override
  String get reviewWorkspaceAllViewedTitle => 'Ya viste cada archivo';

  @override
  String reviewWorkspaceAllViewedMessage(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count notas están en el mensaje, listas para enviar desde la conversación.',
      one:
          '1 nota está en el mensaje, lista para enviar desde la conversación.',
    );
    return '$_temp0';
  }

  @override
  String get reviewWorkspaceBackToChat => 'Volver a la conversación';

  @override
  String reviewWorkspaceCommentOnFile(String file) {
    return 'Comentar sobre $file';
  }

  @override
  String reviewWorkspaceAddFileToPrompt(String file) {
    return 'Agregar $file al mensaje';
  }

  @override
  String get reviewWorkspaceAddComment => 'Agregar el comentario al mensaje';

  @override
  String get reviewWorkspaceCommentEmpty => 'Escribe primero un comentario.';

  @override
  String get reviewWorkspaceCommentLabel => 'Tu comentario';

  @override
  String get reviewWorkspaceCommentHint =>
      '¿Qué debería revisar o cambiar el agente?';

  @override
  String get reviewWorkspaceCommentHelper =>
      'Se conserva si cierras esto, hasta que lo agregues.';

  @override
  String get integrationsMcpTitle => 'Servidores MCP';

  @override
  String get integrationsMcpServersLabel => 'Servidores MCP';

  @override
  String integrationsModelCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count modelos',
      one: '1 modelo',
    );
    return '$_temp0';
  }

  @override
  String integrationsProviderActions(String name) {
    return 'Acciones de $name';
  }

  @override
  String integrationsManageAccounts(String name) {
    return 'Administrar cuentas de $name';
  }

  @override
  String integrationsServerSignIn(String name) {
    return 'Iniciar sesión en $name en el servidor';
  }

  @override
  String get integrationsServerSignInUnavailable =>
      'Este servidor no puede correr un comando de inicio de sesión desde la app.';

  @override
  String integrationsDisconnectNamed(String name) {
    return 'Desconectar $name';
  }

  @override
  String integrationsDisconnectBody(String name) {
    return 'Quita la clave de $name de este servidor. Una respuesta ya en marcha termina primero.';
  }

  @override
  String get integrationsConnectMethodSubtitle => 'Elige cómo conectar';

  @override
  String get integrationsKeyHelper =>
      'La clave se guarda en este servidor. La app nunca la muestra de nuevo.';

  @override
  String get integrationsKeyEmpty => 'Pega primero la clave.';

  @override
  String get integrationsKeyRejected =>
      'El servidor no aceptó esta clave. Revísala y reintenta.';

  @override
  String integrationsSignInAtHost(String host) {
    return '¿Iniciar sesión en $host?';
  }

  @override
  String get integrationsSignInBody =>
      'Aprueba el acceso en tu navegador y luego vuelve a esta app.';

  @override
  String integrationsSignInInstructions(String instructions) {
    return 'El servidor dice: $instructions';
  }

  @override
  String get integrationsFinishSignInTitle => 'Terminar el inicio de sesión';

  @override
  String get integrationsFinishSignInAction => 'Terminar el inicio de sesión';

  @override
  String get integrationsFinishSignInEmpty => 'Pega primero el código.';

  @override
  String get integrationsFinishSignInMcpHelper =>
      'Pega la dirección en la que terminó tu navegador después de aprobar el acceso, o el código que mostró.';

  @override
  String get integrationsFinishSignInProviderHelper =>
      'Pega el código que mostró la página de inicio de sesión después de aprobar el acceso.';

  @override
  String integrationsOAuthInputsContinue(String name) {
    return 'Abrir el inicio de sesión de $name';
  }

  @override
  String get integrationsCancelSignIn => 'Cancelar el inicio de sesión';

  @override
  String get integrationsPendingNotRecoverable =>
      'Mantén esta pantalla abierta hasta terminar: este servidor no puede retomar un inicio de sesión si te vas.';

  @override
  String integrationsMcpActions(String name) {
    return 'Acciones de $name';
  }

  @override
  String integrationsMcpSignIn(String name) {
    return 'Iniciar sesión en $name';
  }

  @override
  String integrationsMcpReconnect(String name) {
    return 'Reconectar $name';
  }

  @override
  String integrationsMcpSigningIn(String name) {
    return 'Iniciando sesión en $name';
  }

  @override
  String get integrationsMcpSignInOnServer =>
      'Inicia sesión en la computadora del servidor; este servidor no puede hacerlo desde la app.';

  @override
  String integrationsMcpRemoveUntilRestart(String name) {
    return 'Quitar $name hasta el reinicio';
  }

  @override
  String integrationsMcpRemoveTitle(String name) {
    return '¿Quitar $name hasta el reinicio?';
  }

  @override
  String get integrationsMcpRemoveBody =>
      'Sus herramientas dejan de funcionar en este proyecto ahora. Si está en la configuración del servidor, vuelve cuando el servidor se reinicie.';

  @override
  String get integrationsMcpRemoveConfirm => 'Quitar hasta el reinicio';

  @override
  String get integrationsCopyResourceAddress => 'Copiar la dirección';

  @override
  String get terminalScreenSourceLabel => 'Dónde se ejecuta el shell';

  @override
  String get terminalScreenNameLabel => 'Nombre';

  @override
  String get terminalScreenRenameConfirm => 'Renombrar';

  @override
  String get terminalScreenNameEmpty => 'Escribe un nombre.';

  @override
  String terminalScreenStopTitle(String name) {
    return '¿Detener $name?';
  }

  @override
  String get terminalScreenStopBody =>
      'El programa y todo lo que inició se detienen, y el terminal desaparece. Su salida no se puede recuperar.';

  @override
  String terminalScreenRemoveTitle(String name) {
    return '¿Quitar $name?';
  }

  @override
  String get terminalScreenRemoveBody =>
      'El terminal y su salida se borran. No se puede deshacer.';

  @override
  String get terminalScreenStopConfirm => 'Detener terminal';

  @override
  String get terminalScreenRemoveConfirm => 'Quitar terminal';

  @override
  String get terminalScreenCreateFailed => 'No se pudo iniciar un terminal';

  @override
  String terminalScreenRemoveEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Quitar $count terminales finalizados',
      one: 'Quitar 1 terminal finalizado',
    );
    return '$_temp0';
  }

  @override
  String terminalScreenRemoveEndedTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '¿Quitar $count terminales finalizados?',
      one: '¿Quitar 1 terminal finalizado?',
    );
    return '$_temp0';
  }

  @override
  String get terminalScreenRemoveEndedBody =>
      'Su salida también se borra. Los terminales en ejecución se conservan.';

  @override
  String get terminalScreenUsePhone => 'Usar el terminal de este teléfono';

  @override
  String terminalScreenRowRunning(String command) {
    return 'En ejecución · $command';
  }

  @override
  String terminalScreenRowEnded(String code, String command) {
    return 'Finalizado · código $code · $command';
  }

  @override
  String terminalScreenRowEndedNoCode(String command) {
    return 'Finalizado · $command';
  }

  @override
  String terminalScreenMenuLabel(String name) {
    return 'Acciones de $name';
  }

  @override
  String terminalScreenOpen(String name) {
    return 'Abrir $name';
  }

  @override
  String terminalScreenRename(String name) {
    return 'Renombrar $name';
  }

  @override
  String terminalScreenStop(String name) {
    return 'Detener $name';
  }

  @override
  String terminalScreenRemove(String name) {
    return 'Quitar $name';
  }

  @override
  String get terminalScreenLoading => 'Cargando terminales';

  @override
  String get terminalScreenPaused =>
      'En pausa mientras la app está en segundo plano';

  @override
  String get terminalScreenConnecting => 'Conectando con el terminal';

  @override
  String get terminalScreenCopy => 'Copiar salida';

  @override
  String terminalScreenPaste(String name) {
    return 'Pegar en $name';
  }

  @override
  String get terminalScreenDetails => 'Detalles del terminal';

  @override
  String terminalScreenDetailsTitle(String name) {
    return 'Detalles de $name';
  }

  @override
  String get terminalScreenDetailCommand => 'Comando';

  @override
  String get terminalScreenDetailFolder => 'Carpeta';

  @override
  String get terminalScreenDetailPid => 'ID de proceso';

  @override
  String get terminalScreenDetailExit => 'Código de salida';

  @override
  String localTerminalStopNamedTitle(String name) {
    return '¿Detener $name?';
  }

  @override
  String localTerminalPasteNamed(String name) {
    return 'Pegar en $name';
  }

  @override
  String get localTerminalCopySelection => 'Copiar la selección';

  @override
  String defaultShellOnlyOne(String name) {
    return '$name · la única shell que este servidor ofrece';
  }

  @override
  String defaultShellSaveFailed(String error) {
    return 'No se pudo cambiar la shell. $error Toca para reintentar.';
  }

  @override
  String get terminalScreenReadableMode => 'Mostrar como texto legible';

  @override
  String get terminalScreenLiveMode => 'Mostrar como terminal en vivo';

  @override
  String get localTerminalSetUpLinux => 'Configurar Linux en este teléfono';

  @override
  String get messageViewSendAgain => 'Enviar este mensaje otra vez';

  @override
  String get messageViewContinueReply => 'Continuar esta respuesta';

  @override
  String get reviewRevertSheetTitle => '¿Deshacer desde este mensaje?';

  @override
  String get reviewRevertSheetBody =>
      'Este mensaje y todo lo que viene después quedan ocultos mientras revisas. Nada es definitivo hasta que elijas.';

  @override
  String get reviewRevertPromptLabel => 'Desde este mensaje';

  @override
  String get reviewRevertFilesToggle => 'Devolver también los archivos';

  @override
  String get reviewRevertFilesToggleHint =>
      'Los archivos vuelven a como estaban antes de este mensaje.';

  @override
  String get reviewRevertSheetAction => 'Deshacer y revisar';

  @override
  String get reviewRevertStageFailed =>
      'No se pudo preparar el deshacer. Nada quedó oculto.';

  @override
  String get reviewRevertScreenTitle => 'Revisar el deshacer';

  @override
  String get reviewRevertScreenIntro =>
      'Este mensaje y todo lo que viene después quedan ocultos. Nada es definitivo hasta que elijas abajo.';

  @override
  String get reviewRevertFilesLabel => 'Archivos en este deshacer';

  @override
  String get reviewRevertNoFiles => 'Ningún archivo cambia con este deshacer.';

  @override
  String reviewRevertFileLines(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String reviewRevertFileSupporting(String folder, String lines) {
    return '$folder · $lines';
  }

  @override
  String get reviewRevertRestoreTitle => 'Devolverlo todo';

  @override
  String get reviewRevertKeepTitle => 'Eliminar los mensajes ocultos';

  @override
  String reviewRevertKeepConfirmTitle(String title) {
    return '¿Eliminar los mensajes de “$title”?';
  }

  @override
  String get reviewRevertKeepConfirmBody => 'Esto no se puede deshacer.';

  @override
  String get reviewRevertKeepConfirmAction => 'Eliminar los mensajes ocultos';

  @override
  String get reviewRevertKeepConsequenceMessages =>
      'El mensaje oculto y todos los mensajes posteriores se eliminan';

  @override
  String get reviewRevertKeepConsequenceFiles =>
      'Los archivos se quedan como están ahora';

  @override
  String reviewRevertRestoreConfirmTitle(String title) {
    return '¿Restaurar “$title”?';
  }

  @override
  String get reviewRevertRestoreConfirmBody =>
      'Los mensajes ocultos vuelven, y los archivos de este deshacer regresan a como estaban cuando lo preparaste. Después puedes deshacer desde un mensaje otra vez.';

  @override
  String get reviewRevertRestoreConsequenceMessages =>
      'Los mensajes ocultos vuelven';

  @override
  String reviewRevertRestoreConsequenceFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count archivos se reemplazan, con las ediciones hechas desde entonces',
      one: '1 archivo se reemplaza, con las ediciones hechas desde entonces',
    );
    return '$_temp0';
  }

  @override
  String get reviewRevertRestoreConsequenceUnknownFiles =>
      'Los archivos de este deshacer se reemplazan, con las ediciones hechas desde entonces';

  @override
  String get reviewRevertStaleTitle => 'El deshacer cambió';

  @override
  String get reviewRevertNoneTitle => 'Nada por revisar';

  @override
  String get reviewRevertNoneBody =>
      'No hay ningún deshacer en espera en esta conversación.';

  @override
  String get reviewRevertBackAction => 'Volver a la conversación';

  @override
  String get reviewRevertKeptTitle => 'Deshacer conservado';

  @override
  String get reviewRevertKeptBody =>
      'Los mensajes ocultos se eliminaron. Los archivos se quedan como están.';

  @override
  String get reviewRevertRestoredTitle => 'Todo volvió';

  @override
  String get reviewRevertRestoredBody =>
      'Los mensajes y los archivos volvieron como estaban.';

  @override
  String get reviewRevertFailed =>
      'No terminó. Revisa la conversación y reintenta.';

  @override
  String get perfTraceClearTimings => 'Borrar las mediciones';

  @override
  String appDiagnosticsClearTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '¿Borrar $count errores?',
      one: '¿Borrar 1 error?',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Los $count errores conservados en este teléfono se eliminan, también del informe guardado. Esto no se puede deshacer.',
      one:
          'El error conservado en este teléfono se elimina, también del informe guardado. Esto no se puede deshacer.',
    );
    return '$_temp0';
  }

  @override
  String appDiagnosticsClearConfirm(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Borrar $count errores',
      one: 'Borrar 1 error',
    );
    return '$_temp0';
  }

  @override
  String get capabilityStateHere => 'Funciona aquí';

  @override
  String get capabilityStateNotServer => 'No en este servidor';

  @override
  String get capabilityStateNotDevice => 'No en este dispositivo';

  @override
  String get capabilityNeedsAndroid => 'Requiere la app de Android';

  @override
  String capabilityAvailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count funciones funcionan aquí',
      one: '1 función funciona aquí',
    );
    return '$_temp0';
  }

  @override
  String get capabilityAvailableCountDetail =>
      'Mostrar lo que este servidor y este dispositivo pueden hacer';

  @override
  String get capabilityAddServer => 'Agregar un servidor que las tenga';

  @override
  String get capabilityAddServerDetail =>
      'Conecta otra computadora o configura una en este teléfono y luego cámbiate a ella';

  @override
  String get keepRunningAllSetTitle => 'Todo listo';

  @override
  String get keepRunningAllSetBody =>
      'Android mantiene la app en ejecución en segundo plano. No hay nada más que permitir en este teléfono.';

  @override
  String get keepRunningDailyLimit =>
      'En Android 15 y posteriores, Android permite unas 6 horas al día de sincronización en segundo plano, aunque todo lo de aquí esté permitido. Después, la app se pausa en segundo plano hasta que la abras.';

  @override
  String get aboutTitle => 'Acerca de';

  @override
  String get aboutCopyVersion => 'Copiar la versión';

  @override
  String get aboutCheckUpdates => 'Buscar actualizaciones';

  @override
  String get aboutUpdateIdle => 'Busca una versión más nueva de esta app';

  @override
  String get aboutUpdateChecking => 'Revisando…';

  @override
  String get aboutUpdateCurrent => 'Tienes la última versión';

  @override
  String get aboutUpdateDownloading => 'Descargando la actualización…';

  @override
  String get aboutUpdateReady =>
      'Actualización lista. Cierra y abre la app para usarla.';

  @override
  String get aboutUpdateCannot =>
      'Esta compilación no puede actualizarse por sí misma. Instala el lanzamiento más reciente.';

  @override
  String get aboutUpdateFailed =>
      'No se pudo buscar actualizaciones. Revisa tu conexión y reintenta.';

  @override
  String get aboutAllLicences => 'Licencias de todos los paquetes';

  @override
  String get aboutAllLicencesDetail =>
      'El texto de licencia de cada biblioteca incluida en esta compilación';

  @override
  String get aboutPackageId => 'ID del paquete';

  @override
  String get usageHubUnavailableTitle => 'No hay consumo por mostrar';

  @override
  String get usageHubUnavailableBody =>
      'Conéctate a un servidor guardado para ver lo que gastó y lo que queda en tus cuentas de proveedor.';

  @override
  String get voiceSetupSubtitle =>
      'Descarga una vez un modelo de voz. Después, la entrada por voz corre en este teléfono sin internet.';

  @override
  String voiceSetupDownloadPack(String model, String size) {
    return 'Descargar $model ($size)';
  }

  @override
  String voiceSetupUsePack(String model) {
    return 'Usar $model';
  }

  @override
  String voiceSetupRedownloadPack(String model) {
    return 'Descargar otra vez el modelo de voz $model';
  }

  @override
  String voiceSetupDeletePack(String model, String size) {
    return 'Eliminar el modelo de voz $model ($size)';
  }

  @override
  String voiceSetupKeepPack(String model) {
    return 'Conservar $model';
  }

  @override
  String voiceSetupDownloadingPack(String model) {
    return 'Descargando $model';
  }

  @override
  String get voiceSetupModelLabel => 'Modelo de voz';

  @override
  String get voiceNoticesTitle => 'Licencias de voz';

  @override
  String get voiceNoticesIntro =>
      'La entrada por voz está construida sobre estas piezas de código abierto. Abre una para leer su licencia.';

  @override
  String voiceNoticesMadeBy(String maker, String license) {
    return '$maker · $license';
  }

  @override
  String voiceNoticesOpenWebsite(String name) {
    return 'Abrir el sitio web de $name';
  }

  @override
  String get voiceNoticesWhisper => 'Modelos de voz Whisper';

  @override
  String get voiceSetupBusyReason => 'Disponible después de la descarga';

  @override
  String get shorebirdUpdateReadyTitle => 'Actualización de la app lista';

  @override
  String get shorebirdUpdateReadyBody =>
      'Entra en vigor cuando cierres la app por completo y la abras de nuevo.';

  @override
  String desktopReleaseAvailable(String tag) {
    return 'Está disponible la actualización $tag';
  }

  @override
  String get desktopReleaseWhatChanged =>
      'La página del lanzamiento lista lo que cambió y tiene las descargas.';

  @override
  String get desktopReleaseOpenPage => 'Abrir la página del lanzamiento';

  @override
  String get appUpdatePromptTitle => 'Actualización disponible';

  @override
  String get appUpdateDownloadAndUpdate => 'Descargar y actualizar';

  @override
  String get appUpdateLater => 'Más tarde';

  @override
  String appUpdateSize(String size) {
    return 'Tamaño: $size';
  }

  @override
  String get appUpdateChangelog => 'Novedades';

  @override
  String get appUpdateInstall => 'Instalar';

  @override
  String get appUpdateDownloading => 'Descargando actualización…';

  @override
  String get appUpdateReadyToInstall =>
      'Listo para instalar. Toca Instalar para finalizar la actualización.';

  @override
  String get appUpdateDownloadFailed =>
      'Error al descargar la actualización. Comprueba la conexión y vuelve a intentarlo.';

  @override
  String get appUpdateRetry => 'Reintentar';

  @override
  String get runningWorkTitle => 'Trabajo en esta conversación';

  @override
  String get runningWorkFailed => 'Falló';

  @override
  String runningWorkAgentState(String state) {
    return 'Agente · $state';
  }

  @override
  String runningWorkCommandState(String state) {
    return 'Comando · $state';
  }

  @override
  String get runningWorkOffline =>
      'Reconectando. Reintenta cuando el servidor responda.';

  @override
  String runningWorkStopAgent(String title) {
    return 'Detener “$title”';
  }

  @override
  String runningWorkStopAgentTitle(String title) {
    return '¿Detener “$title”?';
  }

  @override
  String get runningWorkStopAgentBody =>
      'El agente se detiene donde está. Su conversación y los archivos que cambió se conservan.';

  @override
  String get runningWorkStopAgentConfirm => 'Detener el agente';

  @override
  String get runningWorkScopeChangedTitle => 'Cambió el servidor o el proyecto';

  @override
  String get runningWorkAgentsFailed =>
      'No se pudieron cargar los agentes de esta conversación.';

  @override
  String get runningWorkCommandsFailed =>
      'No se pudieron cargar los comandos de esta conversación.';

  @override
  String get runningWorkEmptyTitle => 'Nada en ejecución';

  @override
  String get runningWorkEmptyBody =>
      'Los agentes y comandos que esta conversación inicie aparecen aquí mientras corren y después de terminar.';

  @override
  String get runningWorkBackgroundBody =>
      'El trabajo sigue corriendo en el servidor y sus resultados vuelven aquí.';

  @override
  String get runningWorkBackgroundAction => 'Seguir conversando mientras corre';

  @override
  String get shellOutputCopyFirst => 'Copia primero la salida';

  @override
  String get shellOutputLimitTitle => 'Detenerlo después de…';

  @override
  String shellOutputStopsIn(String time) {
    return 'se detiene en $time';
  }

  @override
  String get shellOutputNoLimit => 'sin límite de tiempo';

  @override
  String shellOutputAboutToStop(String time) {
    return 'Se detiene en $time. Cambia el límite de tiempo para darle más.';
  }

  @override
  String get shellOutputReadFailed => 'No se pudo leer la salida.';

  @override
  String get shellOutputLimitFailed =>
      'No se pudo cambiar el límite de tiempo.';

  @override
  String get shellOutputDetailCommand => 'Comando tal como se escribió';

  @override
  String get shellOutputDetailFolder => 'Carpeta';

  @override
  String get shellOutputDetailExit => 'Código de salida';

  @override
  String get shellOutputDetailId => 'ID del comando';

  @override
  String get shellOutputReading => 'Leyendo la salida';

  @override
  String get sessionDestinationWarpTitle => 'Mover a la nube';

  @override
  String get sessionDestinationSeparateCopy => 'Copia aparte';

  @override
  String sessionDestinationCloudKind(String state) {
    return 'Máquina en la nube · $state';
  }

  @override
  String get sessionDestinationConnected => 'Conectada';

  @override
  String get sessionDestinationNotConnected => 'No conectada';

  @override
  String get sessionDestinationNotConnectedWhy =>
      'No conectada. Se puede elegir una vez que conecte.';

  @override
  String sessionDestinationChangesGo(String destination) {
    return 'Con cambios, van con ella a $destination.';
  }

  @override
  String sessionDestinationChangesCopied(String destination) {
    return 'Con cambios, una copia va con ella a $destination.';
  }

  @override
  String sessionDestinationChangesStay(String place) {
    return 'Sin cambios, se quedan en $place.';
  }

  @override
  String sessionDestinationMoveWithout(String destination) {
    return 'Mover a $destination sin cambios';
  }

  @override
  String get sessionDestinationMoveFailed =>
      'No se pudo mover la conversación.';

  @override
  String get sessionDestinationLoadFailed =>
      'No se pudieron cargar los destinos';

  @override
  String get sessionDestinationNoneTitle => 'No hay dónde moverla';

  @override
  String get sessionDestinationNoneMoveBody =>
      'Este proyecto solo tiene esta carpeta. Una copia aparte del proyecto aparece aquí en cuanto exista.';

  @override
  String get sessionDestinationNoneWarpBody =>
      'Este proyecto aún no tiene máquina en la nube.';

  @override
  String get consoleOrganizationWhatChanges =>
      'Los modelos, proveedores y facturación siguen a la organización que elijas.';

  @override
  String consoleOrganizationSwitchBody(String organization) {
    return '$organization pasa a ser la organización para modelos, proveedores y facturación. Los modelos se recargan; nada en ejecución se detiene.';
  }

  @override
  String consoleOrganizationSwitchConfirm(String organization) {
    return 'Cambiar a $organization';
  }

  @override
  String get consoleOrganizationLoadFailed =>
      'No se pudieron cargar tus organizaciones';

  @override
  String get consoleOrganizationNoneTitle => 'No hay organizaciones';

  @override
  String get consoleOrganizationOnlyOne =>
      'Esta es tu única organización, así que no hay a cuál cambiarse.';

  @override
  String get sessionContextLoading => 'Cargando el contexto';

  @override
  String get sessionContextMovedTitle => 'Esta conversación se movió';

  @override
  String get sessionContextLoadFailed => 'No se pudo cargar el contexto';

  @override
  String get sessionContextRefreshFailed =>
      'No se pudo actualizar. Los números de abajo vienen de la última lectura.';

  @override
  String sessionContextVerdictPlenty(String percent) {
    return '$percent% usado · queda de sobra';
  }

  @override
  String sessionContextVerdictUsed(String percent) {
    return '$percent% usado';
  }

  @override
  String sessionContextVerdictNear(String percent) {
    return '$percent% usado';
  }

  @override
  String sessionContextVerdictFull(String percent) {
    return '$percent% usado · al límite';
  }

  @override
  String get sessionContextNearLimitTitle => 'Cerca del límite';

  @override
  String get sessionContextNearLimitBody =>
      'Los detalles más viejos pueden quedarse fuera de lo que el modelo ve. Compacta la conversación para seguir, o empieza una nueva.';

  @override
  String get sessionContextCompactAction => 'Compactar esta conversación';

  @override
  String get sessionContextCompactTitle => '¿Compactar esta conversación?';

  @override
  String get sessionContextCompactBody =>
      'OpenCode resume la conversación hasta ahora y continúa desde el resumen, así que toma menos del límite del modelo.';

  @override
  String get sessionContextCompactKept =>
      'Cada mensaje se queda en el historial.';

  @override
  String get sessionContextCompactConfirm => 'Compactar la conversación';

  @override
  String get sessionContextCompactStarted =>
      'La compactación empezó. Los números se actualizan cuando termine.';

  @override
  String get sessionContextCompactBusy => 'Espera a que termine la respuesta.';

  @override
  String get sessionContextMakeupTitle => 'Entrada de la última petición';

  @override
  String sessionContextTokens(String count) {
    return '$count tokens';
  }

  @override
  String get sessionContextModelId => 'ID del modelo';

  @override
  String get demoScreenTitle => 'Pruébalo sin conexión';

  @override
  String get demoScreenSimulated => 'Simulado · no se guarda nada';

  @override
  String get demoScreenFinished =>
      'Ese es todo el ciclo: un mensaje, una respuesta y una edición revisada.';

  @override
  String sessionContextPercent(String percent) {
    return '$percent %';
  }

  @override
  String sessionDestinationChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos cambiados están presentes.',
      one: '1 archivo cambiado está presente.',
    );
    return '$_temp0';
  }

  @override
  String get activeContextLoading => 'Leyendo el contexto activo…';

  @override
  String activeContextAllCount(int count) {
    return 'Todos los mensajes · $count';
  }

  @override
  String get activeContextChangedTitle => 'Esta vista está desactualizada';

  @override
  String get activeContextFailedTitle => 'No se pudo leer el contexto';

  @override
  String get activeContextIntro =>
      'Lo que el modelo lee en su próximo turno, después del último resumen.';

  @override
  String get activeContextEmptyDetail =>
      'Aún no se conserva nada para el próximo turno. Desliza hacia abajo para revisar otra vez.';

  @override
  String get activeContextWhat => 'contexto activo';

  @override
  String activeContextRowMenu(String type) {
    return 'Acciones para $type';
  }

  @override
  String activeContextOpenMessage(String type) {
    return 'Abrir $type';
  }

  @override
  String activeContextCopyMessage(String type) {
    return 'Copiar el texto de $type';
  }

  @override
  String get activeContextMessageId => 'ID del mensaje';

  @override
  String activeContextCopyPart(String part) {
    return 'Copiar $part';
  }

  @override
  String get sessionNoteDeleting => 'Eliminando la nota…';

  @override
  String get sessionNoteSaving => 'Guardando la nota…';

  @override
  String get sessionNoteLoading => 'Leyendo la nota guardada…';

  @override
  String get sessionNoteLoadFailed => 'No se pudo leer la nota';

  @override
  String get sessionNoteSaveFailed => 'No se pudo guardar la nota';

  @override
  String get sessionNoteFieldLabel => 'Nota';

  @override
  String get sessionNoteFieldLocked =>
      'Actualiza la nota guardada antes de editarla.';

  @override
  String sessionNoteTooLong(int over, int limit) {
    return '$over bytes de más. Una nota puede tener hasta $limit bytes.';
  }

  @override
  String get sessionNoteWriteFirst => 'Escribe una nota para guardarla.';

  @override
  String get sessionNoteEmptyUseDelete =>
      'Para quitar la nota, usa Borrar la nota guardada.';

  @override
  String get sessionRelationsTitle => 'Sub-agentes';

  @override
  String sessionRelationsStopTitle(String title) {
    return '¿Detener $title?';
  }

  @override
  String get sessionRelationsStopBody =>
      'El sub-agente detiene su paso actual. Lo que ya hizo se queda en su conversación.';

  @override
  String get sessionRelationsStopConfirm => 'Detener el sub-agente';

  @override
  String get sessionRelationsFailedTitle =>
      'No se pudieron cargar los sub-agentes';

  @override
  String get sessionRelationsLoading => 'Cargando sub-agentes…';

  @override
  String get sessionRelationsStartedFrom => 'Empezó en';

  @override
  String sessionRelationsSubagentCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count sub-agentes',
      one: '1 sub-agente',
    );
    return '$_temp0';
  }

  @override
  String get sessionRelationsOpenToAnswer => 'abierto a responder';

  @override
  String get sessionRelationsIdle => 'Inactivo';

  @override
  String get sessionRelationsThisConversation => 'Esta conversación';

  @override
  String get sessionRelationsOpening => 'Abriendo…';

  @override
  String sessionRelationsRowMenu(String title) {
    return 'Acciones para $title';
  }

  @override
  String sessionRelationsOpen(String title) {
    return 'Abrir $title';
  }

  @override
  String sessionRelationsCopyHandoff(String title) {
    return 'Continuar $title en la computadora';
  }

  @override
  String sessionRelationsPin(String title) {
    return 'Fijar $title';
  }

  @override
  String sessionRelationsUnpin(String title) {
    return 'Soltar $title';
  }

  @override
  String sessionRelationsStop(String title) {
    return 'Detener $title';
  }

  @override
  String get webSourcesInvalidUrl =>
      'Escribe una dirección HTTP o HTTPS sin nombre de usuario ni contraseña.';

  @override
  String get webSearchFailedTitle => 'La búsqueda no terminó';

  @override
  String get webSearchTryAgain => 'Buscar otra vez';

  @override
  String get webSearchBusy => 'Espera a que termine la búsqueda.';

  @override
  String get webSearchQueryHint => 'Por ejemplo: flutter golden tests';

  @override
  String get webSearchNeedsProvider =>
      'Configura primero un proveedor de búsqueda en este servidor.';

  @override
  String get webSearchEmptyDetail =>
      'Prueba otras palabras, o pega un enlace abajo.';

  @override
  String webSearchResults(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count resultados',
      one: '1 resultado',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesAdded => 'Agregado';

  @override
  String webSourcesAddNamed(String title) {
    return 'Agregar $title al mensaje';
  }

  @override
  String webSourcesRowMenu(String title) {
    return 'Acciones para $title';
  }

  @override
  String webSourcesOpenHost(String host) {
    return 'Abrir $host en el navegador';
  }

  @override
  String get webSourcesAddLink => 'Agregar el enlace al mensaje';

  @override
  String get webSourcesPasteDetail =>
      'Una dirección pública, con un extracto opcional';

  @override
  String webSourcesRemoveNamed(String title) {
    return 'Quitar $title del mensaje';
  }

  @override
  String get webSearchSearching => 'Buscando…';

  @override
  String get webSearchFindingProviders => 'Buscando proveedores de búsqueda…';

  @override
  String webSourcesDone(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Agregar $count fuentes al mensaje',
      one: 'Agregar 1 fuente al mensaje',
    );
    return '$_temp0';
  }

  @override
  String get webSourcesScopeChangedTitle => 'El servidor cambió';

  @override
  String get sessionExportFormatLabel => 'Formato';

  @override
  String get sessionExportJsonUnavailable =>
      'Este servidor no puede enviar una copia completa. Guarda la transcripción legible en su lugar.';

  @override
  String get sessionExportPrivacyLabel => 'Privacidad';

  @override
  String get sessionExportRedactKeeps =>
      'Conserva quién escribió cada mensaje; las palabras se vuelven marcadores. No es una copia de seguridad.';

  @override
  String get sessionExportRedactBusy =>
      'Espera a que se guarde el archivo para cambiar esto.';

  @override
  String get sessionExportRedactChanged =>
      'Abre la exportación otra vez desde la conversación para cambiar esto.';

  @override
  String get sessionExportSaveJson => 'Guardar la conversación completa';

  @override
  String get sessionExportSaveMarkdown => 'Guardar la transcripción legible';

  @override
  String get sessionExportSaveFailed =>
      'No se pudo escribir el archivo en este dispositivo. Nada cambió en el servidor. Reintenta, o elige otra carpeta.';

  @override
  String get capabilitiesToolsMissingTitle =>
      'Las herramientas no están listadas';

  @override
  String capabilitiesToolsMissingOnServer(String server) {
    return '$server no lista sus herramientas';
  }

  @override
  String get mcpSetupWhere => 'Dónde va';

  @override
  String get mcpSetupHowItRuns => 'Cómo corre';

  @override
  String get mcpSetupHeaders => 'Encabezados';

  @override
  String get mcpSetupAdvanced => 'Avanzado';

  @override
  String get mcpSetupAdvancedRemote =>
      'Detección de inicio de sesión y límite de tiempo';

  @override
  String get mcpSetupAdvancedLocal => 'Carpeta de trabajo y límite de tiempo';

  @override
  String get mcpSetupNoProject => 'Abre primero un proyecto';

  @override
  String get mcpSetupRuntimeNote =>
      'Conecta ahora y desaparece cuando OpenCode se reinicie. Para una configuración permanente, edita la configuración del servidor.';

  @override
  String mcpSetupSaveNamed(String name) {
    return 'Guardar $name';
  }

  @override
  String mcpSetupAddNamed(String name) {
    return 'Agregar $name';
  }

  @override
  String get mcpSetupLocationChangedShort => 'El servidor o el proyecto cambió';

  @override
  String get mcpSetupSaveFailed => 'No se pudo agregar el servidor MCP';

  @override
  String get mcpSetupDiscardTitle => '¿Descartar este servidor MCP?';

  @override
  String get mcpSetupDiscardBody =>
      'Lo que escribiste aquí no se guarda y se perderá.';

  @override
  String get mcpSetupDiscardConfirm => 'Descartar el servidor';

  @override
  String get externalAgentsEmptyTitle => 'Aún no hay agentes externos';

  @override
  String get externalAgentsEmptyBody =>
      'Agrega uno por su dirección web. Ves lo que dice sobre sí mismo antes de que se guarde nada.';

  @override
  String get externalAgentsBoundary =>
      'Solo el texto que envías alcanza a un agente externo. Tus proyectos, archivos y otras conversaciones se quedan en este teléfono.';

  @override
  String get externalAgentsRemovalIncomplete =>
      'La eliminación no terminó · toca para reintentar';

  @override
  String externalAgentsRemoveNamed(String name) {
    return 'Quitar $name de este teléfono';
  }

  @override
  String externalAgentsRemoveTitle(String name) {
    return '¿Quitar $name?';
  }

  @override
  String get externalAgentsRemoveBody =>
      'Sus tareas guardadas y su clave salen de este teléfono. El trabajo que ya empezó sigue en marcha, y lo que él conserva se queda consigo.';

  @override
  String get externalAgentsBusy => 'Espera a que termine el paso actual';

  @override
  String get externalAgentsAddressHelper =>
      'Su dirección web, o la dirección de su Agent Card.';

  @override
  String get externalAgentsCheck => 'Revisar el agente';

  @override
  String get externalAgentsCheckNeedsAddress =>
      'Escribe primero la dirección del agente';

  @override
  String get externalAgentsStopChecking => 'Detener la revisión';

  @override
  String get externalAgentsCheckFailedTitle => 'No se pudo revisar este agente';

  @override
  String externalAgentsSaveNamed(String name) {
    return 'Guardar $name';
  }

  @override
  String get externalAgentsSaveNeedsKey =>
      'Escribe primero la clave del agente';

  @override
  String get externalAgentsAboutLabel => 'Lo que dice sobre sí mismo';

  @override
  String get externalAgentsUnverified =>
      'El agente se describe a sí mismo. Esta app no ha verificado quién lo corre, qué puede hacer ni cuánto cuesta.';

  @override
  String get externalAgentsUnsupportedTitle => 'Agente no admitido';

  @override
  String get externalAgentsUnsupportedBody =>
      'No toma tareas de texto como las envía esta app, o pide un inicio de sesión que esta app no admite.';

  @override
  String get externalAgentsKeyLabel => 'Clave del agente';

  @override
  String get externalAgentsKeyHelper =>
      'La clave que su dueño te dio. Se queda en el almacenamiento seguro de este teléfono y se envía solo a este agente.';

  @override
  String get externalAgentsNoKey =>
      'Este agente no pide clave. No envíes información privada a menos que confíes en él.';

  @override
  String get externalAgentsDetailCard => 'Agent Card';

  @override
  String get externalAgentsDetailEndpoint => 'Endpoint';

  @override
  String get externalAgentsDetailVersion => 'Versión';

  @override
  String get externalAgentsDetailConnection => 'Conexión';

  @override
  String externalAgentsNewTaskNamed(String name) {
    return 'Tarea nueva para $name';
  }

  @override
  String externalAgentsReplaceKeyNamed(String name) {
    return 'Reemplazar la clave de $name';
  }

  @override
  String externalAgentsReplaceKeyTitle(String name) {
    return 'Reemplazar la clave de $name';
  }

  @override
  String get externalAgentsSaveKey => 'Guardar la clave';

  @override
  String get externalAgentsTasksLabel => 'Tareas';

  @override
  String get externalAgentsNoTasksTitle => 'Aún no hay tareas';

  @override
  String get externalAgentsNoTasksBody =>
      'Escribe una tarea y revísala antes de que se envíe. Abrir una tarea enviada pregunta por su estado; nunca se envía dos veces.';

  @override
  String get externalAgentsUntitledTask => 'Tarea nueva';

  @override
  String externalAgentsSendNamed(String name) {
    return 'Enviar a $name';
  }

  @override
  String externalAgentsReplyNamed(String name) {
    return 'Responder a $name';
  }

  @override
  String get externalAgentsSendNeedsText => 'Escribe primero la tarea';

  @override
  String get externalAgentsReplyNeedsText => 'Escribe primero tu respuesta';

  @override
  String get externalAgentsSendNote =>
      'Solo se envía este texto. El agente puede usar sus propios servicios y cobrar por ellos; revisa sus términos.';

  @override
  String externalAgentsCheckedAt(String age) {
    return 'Revisado con el agente hace $age';
  }

  @override
  String get externalAgentsPullToCheck =>
      'Guardado en este teléfono · desliza hacia abajo para revisar con el agente';

  @override
  String externalAgentsStopMenu(String name) {
    return 'Pedirle a $name detener esta tarea';
  }

  @override
  String get externalAgentsStopUnavailable =>
      'Pregunta primero al agente; desliza hacia abajo para actualizar';

  @override
  String get externalAgentsForgetMenu => 'Olvidar esta tarea en este teléfono';

  @override
  String get externalAgentsForgetTitle => '¿Olvidar esta tarea?';

  @override
  String get externalAgentsForgetBody =>
      'Sale de este teléfono. El trabajo que el agente ya empezó sigue en marcha, y su propia copia se queda consigo.';

  @override
  String get externalAgentsForgetConfirm => 'Olvidar la tarea';

  @override
  String mcpSetupSavedNamed(String name) {
    return '$name guardado en este servidor';
  }

  @override
  String mcpSetupSavedNotConnectedBody(String reason) {
    return 'La app no se reconectó después. $reason';
  }

  @override
  String get mcpSetupSavedElsewhere =>
      'El servidor o el proyecto cambió después de guardar, así que esta página no puede reconectar por él. Ciérrala y revisa los servidores MCP.';

  @override
  String get mcpSetupUnavailableTitle => 'No se pueden agregar servidores MCP';

  @override
  String get mcpSetupUnavailableBody =>
      'No acepta servidores MCP nuevos desde la app. Agrégalos en su configuración en la computadora; después aparecen en Servidores MCP.';

  @override
  String get commandAuthSheetWorking => 'Preguntando al servidor…';

  @override
  String get credentialSheetLoading => 'Leyendo las cuentas guardadas…';

  @override
  String credentialSheetEmptyBody(String provider) {
    return 'Vuelve a iniciar sesión en $provider desde Proveedores para agregar una cuenta.';
  }

  @override
  String credentialSheetActions(String label) {
    return 'Acciones para $label';
  }

  @override
  String credentialSheetUseNamed(String label) {
    return 'Usar $label';
  }

  @override
  String get credentialSheetInUse => 'Ya en uso';

  @override
  String credentialSheetRenameNamed(String label) {
    return 'Renombrar $label…';
  }

  @override
  String credentialSheetRemoveNamed(String label) {
    return 'Quitar $label';
  }

  @override
  String credentialSheetRemoveBody(String label, String provider) {
    return 'Quita $label de este servidor. Los proyectos que la usan necesitarán otra cuenta de $provider.';
  }

  @override
  String credentialSheetRenamed(String label) {
    return 'Renombrada a $label.';
  }

  @override
  String get credentialSheetLabelEmpty => 'Ponle un nombre a la cuenta.';

  @override
  String get credentialSheetLabelInvalid =>
      'Usa hasta 128 caracteres, sin saltos de línea ni caracteres de control.';

  @override
  String pendingAuthRecoveryForgetTitle(String integration) {
    return '¿Olvidar el inicio de sesión de $integration?';
  }

  @override
  String get pendingAuthRecoveryForgetBody =>
      'La app deja de seguirlo en este dispositivo. No se cancela nada en el servidor; un inicio de sesión sin terminar allí caduca por sí solo.';

  @override
  String get toolsScreenLoadFailed =>
      'No se pudieron cargar las herramientas de este modelo';

  @override
  String get toolsScreenSearchWhat => 'herramientas';

  @override
  String get toolsScreenRegisteredOnly =>
      'Registrado en este proyecto · este modelo no puede llamarlo';

  @override
  String get toolsDetailTakes => 'Toma';

  @override
  String get toolsDetailTakesNothing => 'No toma nada.';

  @override
  String get toolsDetailRequired => 'obligatorio';

  @override
  String get toolsDetailOptional => 'opcional';

  @override
  String get toolsDetailTypeText => 'texto';

  @override
  String get toolsDetailTypeNumber => 'número';

  @override
  String get toolsDetailTypeYesNo => 'sí o no';

  @override
  String get toolsDetailTypeList => 'lista';

  @override
  String get toolsDetailTypeGroup => 'grupo de valores';

  @override
  String get toolsDetailTypeAny => 'cualquier valor';

  @override
  String commandsScreenRunsWith(String agent) {
    return 'Corre con $agent';
  }

  @override
  String get commandsScreenMenuLabel => 'Acciones del comando';

  @override
  String commandsScreenCopy(String command) {
    return 'Copiar $command';
  }

  @override
  String get referencesScreenLoading => 'Cargando referencias';

  @override
  String get referencesScreenLoadFailed =>
      'No se pudieron cargar las referencias';

  @override
  String get referencesScreenIntro =>
      'Carpetas a las que este proyecto apunta a sus agentes. Agrega una a un mensaje y el agente puede leer lo que contiene.';

  @override
  String get referencesScreenEmptyBody =>
      'Una referencia es una carpeta que los agentes del proyecto pueden leer. Las referencias configuradas para este proyecto aparecen aquí.';

  @override
  String get referencesScreenMenuLabel => 'Acciones de la referencia';

  @override
  String referencesScreenAdd(String mention) {
    return 'Agregar $mention al mensaje';
  }

  @override
  String referencesScreenShowDetails(String name) {
    return 'Mostrar los detalles de $name';
  }

  @override
  String referencesScreenCopyMention(String mention) {
    return 'Copiar $mention';
  }

  @override
  String get referencesScreenCopyPath => 'Copiar la ruta';

  @override
  String referencesScreenSheetBody(String mention) {
    return 'Escribe $mention en un mensaje y el agente lee esta carpeta para esa respuesta.';
  }

  @override
  String get referencesScreenPathLabel => 'Ruta';

  @override
  String get skillsScreenLoading => 'Cargando skills';

  @override
  String get skillsScreenLoadFailed => 'No se pudieron cargar las skills';

  @override
  String get skillSheetViewLabel => 'Cómo mostrar la skill';

  @override
  String get skillSheetLocation => 'Archivo';

  @override
  String skillSheetCopyCommand(String command) {
    return 'Copiar $command';
  }

  @override
  String get skillSheetCheckConversation =>
      'Revisa la conversación antes de reintentar.';

  @override
  String get skillSheetSending => 'Agregando la skill…';

  @override
  String toolCardDelegatedTo(String agent) {
    return 'Delegado a $agent';
  }

  @override
  String get toolCardExitPassed => 'Exitoso · código de salida 0';

  @override
  String toolCardExitFailed(int code) {
    return 'Falló · código de salida $code';
  }

  @override
  String get toolCardRunCommandAgain => 'Ejecutar este comando otra vez';

  @override
  String get toolCardCopyCommand => 'Copiar el comando';

  @override
  String toolCardLoadImageAgain(String name) {
    return 'Cargar $name de nuevo';
  }

  @override
  String toolCardChangesIn(String file) {
    return 'Cambios en $file';
  }

  @override
  String mobileTasksShowAll(int count) {
    return 'Mostrar las $count tareas';
  }

  @override
  String get composerBusyReason => 'Preparando tu mensaje…';

  @override
  String get composerToolsTextOnly => 'Este servidor solo acepta texto';

  @override
  String composerToolsAgentTextOnly(String agent) {
    return 'Aquí $agent solo acepta texto';
  }

  @override
  String launcherConversationHint(String project, String agent) {
    return 'Conversación en $project · $agent';
  }

  @override
  String composerToolsAgentPicturesOnly(String agent) {
    return '$agent solo acepta imágenes: usa «Galería de fotos» o «Tomar foto»';
  }

  @override
  String chatsNewAgentNeedsProjectsFolder(String agent) {
    return '$agent solo trabaja en los proyectos de este teléfono. Elige uno de ellos.';
  }

  @override
  String get composerToolCommandsTitle => 'Comandos y agentes';

  @override
  String get composerToolSavedSubtitle =>
      'Vuelve a poner en el borrador un mensaje guardado';

  @override
  String get composerToolSaveForLater => 'Guardar mensaje para después';

  @override
  String get composerToolNothingToSave => 'Escribe o adjunta algo primero';

  @override
  String get composerToolsMore => 'Más herramientas';

  @override
  String get composerReturnedToDraft => 'Devuelto a tu borrador';

  @override
  String get promptHistoryIntro =>
      'Toca un mensaje para agregarlo a tu borrador.';

  @override
  String get promptEditorDiscardChanges => 'Descartar los cambios';

  @override
  String get promptEditorDone => 'Usar en el borrador';

  @override
  String get promptEditorFieldLabel => 'Mensaje';

  @override
  String get promptStashDeleted => 'Mensaje guardado eliminado';

  @override
  String get promptStashIntro =>
      'Primero los más recientes · se conservan en este dispositivo';

  @override
  String get promptStashEmptyTitle => 'Aún no hay mensajes guardados';

  @override
  String get promptStashEmptyBody =>
      'Elige Guardar mensaje para después en el menú + para conservar un mensaje aquí.';

  @override
  String get promptStashBusy => 'Espera a que termine el paso actual';

  @override
  String get promptStashRowActions => 'Acciones del mensaje guardado';

  @override
  String get promptStashRestoreToDraft => 'Restaurar al borrador';

  @override
  String get promptStashDeleteAction => 'Eliminar el mensaje guardado';

  @override
  String get modelShortcutsNextRecent => 'Siguiente modelo reciente';

  @override
  String get modelShortcutsPreviousRecent => 'Modelo reciente anterior';

  @override
  String get modelShortcutsNoRecent =>
      'Usa primero otro modelo para volver a este';

  @override
  String get modelShortcutsNoFavorite =>
      'Primero marca un modelo como favorito en el selector de modelos';

  @override
  String get composerDraftBlockedReason =>
      'Responde primero la pregunta sobre este borrador';

  @override
  String get commandLauncherSubtitle =>
      'Ejecuta una acción en esta conversación, o un comando de este servidor';

  @override
  String get teamChatRefusedTitle => 'Tarea no tomada';

  @override
  String get teamChatRefusedRetry => 'Enviar la tarea otra vez';

  @override
  String get teamChatGoneTitle => 'Tarea ya no listada';

  @override
  String get teamChatGoneBody =>
      'Puede que se haya quitado en la computadora del equipo. La página de AI Team lista las tareas que tiene ahora.';

  @override
  String get teamChatGoneOpenTeam => 'Abrir la página de AI Team';

  @override
  String connectionReconnectTo(String server) {
    return 'Reconectar con $server';
  }

  @override
  String serverDisconnectFrom(String server) {
    return 'Desconectar de $server';
  }

  @override
  String monitorSwitchToTitle(String server) {
    return '¿Cambiar a $server?';
  }

  @override
  String monitorSwitchTo(String server) {
    return 'Cambiar a $server';
  }

  @override
  String servicesStartNamed(String service) {
    return 'Iniciar $service';
  }

  @override
  String servicesStopNamed(String service) {
    return 'Detener $service';
  }

  @override
  String serverSettingsChangeSignIn(String server) {
    return 'Cambiar el inicio de sesión de $server';
  }

  @override
  String serverSettingsAuthBasic(String user) {
    return 'Autenticación básica como $user';
  }

  @override
  String get serverSettingsUpdateHint =>
      'Usa el instalador oficial de OpenCode; reinicia el servidor después.';

  @override
  String get settingsHubModelRow => 'Modelo';

  @override
  String get notifyTurnOnInAndroid => 'Activar las notificaciones en Android';

  @override
  String get serversAddOtherWays => 'O conecta de otra forma';

  @override
  String get libraryImportAConversation => 'Importar una conversación';

  @override
  String get projectHubHealthSubtitle =>
      'Rama, servicios de lenguaje y formateadores';

  @override
  String projectHubChangedFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos cambiados',
      one: '1 archivo cambiado',
      zero: 'Sin cambios',
    );
    return '$_temp0';
  }

  @override
  String projectHubTerminalsRunning(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count en ejecución',
      one: '1 en ejecución',
    );
    return '$_temp0';
  }

  @override
  String get projectHubCopyFolderPath => 'Copiar la ruta de la carpeta';

  @override
  String get terminalScreenNoTerminalThisServer =>
      'Este servidor no comparte un terminal';

  @override
  String terminalScreenNoTerminalNamed(String server) {
    return '$server no comparte un terminal';
  }

  @override
  String get terminalScreenNoTerminalWhy =>
      'Aquí solo se abren terminales de servidores que los comparten.';

  @override
  String get integrationsSignInWaiting => 'Inicio de sesión en espera';

  @override
  String get integrationsSignInMayNotHaveStarted =>
      'Puede que el inicio de sesión no haya comenzado';

  @override
  String get integrationsSignInExpired => 'El inicio de sesión expiró';

  @override
  String get integrationsSignInFailed => 'Falló el inicio de sesión';

  @override
  String get integrationsSignInComplete =>
      'Sesión iniciada · toca para terminar';

  @override
  String integrationsFinishSigningIn(String provider) {
    return 'Terminar de iniciar sesión en $provider';
  }

  @override
  String integrationsEnterCodeFor(String provider) {
    return 'Ingresar el código de $provider';
  }

  @override
  String integrationsCancelSignInFor(String provider) {
    return 'Cancelar el inicio de sesión de $provider';
  }

  @override
  String get integrationsForgetSignInOnPhone =>
      'Olvidar este inicio de sesión en este teléfono';

  @override
  String integrationsSignInActions(String provider) {
    return 'Acciones de inicio de sesión de $provider';
  }

  @override
  String integrationsAccountCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count cuentas',
      one: '1 cuenta',
    );
    return '$_temp0';
  }

  @override
  String get toolsScreenNoBackgroundSubagents =>
      'sin sub-agentes en segundo plano';

  @override
  String get usageRefreshSpending => 'Actualizar el gasto';

  @override
  String modelPickerUseModel(String model) {
    return 'Usar $model';
  }

  @override
  String get modelPickerUseChosenModel => 'Usar modelo';

  @override
  String get handoffUiComputerCommandLabel => 'Comando de terminal';

  @override
  String get formRendererDecline => 'Rechazar esta petición';

  @override
  String get perfTraceActions => 'Acciones del informe de tiempos';

  @override
  String voiceSetupNotDownloaded(String size) {
    return 'No descargado · $size';
  }

  @override
  String get voiceSetupDone => 'Listo';

  @override
  String get voiceAllowMicInSettings =>
      'Permite el micrófono en los ajustes de Android';

  @override
  String sessionContextMessagesSplit(String count, String yours, String agent) {
    return '$count ($yours tuyos, $agent del agente)';
  }

  @override
  String get webSourcesClose => 'Cerrar';

  @override
  String get webSourcesPastedLinks => 'Enlaces que agregaste';

  @override
  String get thisPhoneHostInApp => 'En la app';

  @override
  String get thisPhoneSetUp => 'Configurar OpenCode';

  @override
  String get thisPhoneStart => 'Iniciar el servidor';

  @override
  String get thisPhoneStop => 'Detener OpenCode en este teléfono';

  @override
  String get thisPhoneUpdate => 'Actualizar OpenCode';

  @override
  String thisPhoneUpdateDetail(String version) {
    return 'Instala la versión $version';
  }

  @override
  String get thisPhoneAddTools => 'Agregar herramientas';

  @override
  String get thisPhoneInstalled => 'Instalado';

  @override
  String get thisPhoneTerminal => 'Abrir una terminal';

  @override
  String get thisPhoneStorage => 'Almacenamiento';

  @override
  String get thisPhoneRemove => 'Quitar OpenCode';

  @override
  String get thisPhoneBusy => 'Espera a que termine el paso actual';

  @override
  String removeFromPhoneKeepBody(String size) {
    return 'Se quitan OpenCode y sus herramientas, liberando unos $size.';
  }

  @override
  String get removeFromPhoneKeepBodyUnmeasured =>
      'Se quitan OpenCode y sus herramientas.';

  @override
  String get removeFromPhoneKeepConfirm =>
      'Quitar OpenCode y conservar mis proyectos';

  @override
  String get removeFromPhoneDeleteAll => 'Eliminar todo';

  @override
  String get removeFromPhoneDeleteTitle =>
      '¿Eliminar OpenCode y los proyectos?';

  @override
  String removeFromPhoneDeleteBody(String size) {
    return 'Se eliminan OpenCode, sus herramientas y todos los proyectos de este teléfono, liberando unos $size. Esto no se puede deshacer.';
  }

  @override
  String get removeFromPhoneDeleteBodyUnmeasured =>
      'Se eliminan OpenCode, sus herramientas y todos los proyectos de este teléfono. Esto no se puede deshacer.';

  @override
  String get thisPhoneManage => 'Administrar Este teléfono';

  @override
  String get chatRequestWho => 'El agente';

  @override
  String get chatRequestIfIgnored =>
      'El agente espera hasta que respondas. No se pierde nada.';

  @override
  String chatRequestMoreWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Hay $count solicitudes más en espera.',
      one: 'Hay 1 solicitud más en espera.',
    );
    return '$_temp0';
  }

  @override
  String get chatRequestNoConnection =>
      'No hay conexión con el servidor, así que no se puede responder desde aquí.';

  @override
  String get chatRequestAlwaysTitle => 'Permitir siempre estas solicitudes';

  @override
  String chatRequestAlwaysScope(String patterns, String context) {
    return 'A partir de ahora, $patterns se ejecutará sin preguntarte, $context. Puedes revertirlo en Ajustes.';
  }

  @override
  String chatRequestAlwaysConfirm(String what) {
    return '¿Permitir siempre $what en este proyecto?';
  }

  @override
  String get chatRequestAlwaysInProject => 'en este proyecto';

  @override
  String get chatRequestAlwaysOn => 'Siempre permitido';

  @override
  String get chatRequestDetailTool => 'Herramienta';

  @override
  String get chatRequestDetailPatterns => 'Patrones solicitados';

  @override
  String get chatRequestOtherAnswer => 'Otra respuesta';

  @override
  String get chatRequestOtherField => 'Tu respuesta';

  @override
  String get formFlowAnsweredElsewhereBody =>
      'Este formulario se respondió en otro dispositivo, así que nada se envió desde este teléfono.';

  @override
  String get approvalsUiPausedDetail =>
      'Este teléfono no está conectado. La aprobación automática se reanuda cuando se reconecte.';

  @override
  String get teamUiHomeRunReviewNext => 'un revisor lo revisa después';

  @override
  String teamUiGateRunStoppedTitle(String title) {
    return '$title se detuvo';
  }

  @override
  String get phoneServerNameInSentence => 'el Ubuntu de la app';

  @override
  String get teamAgentWorkUnblockedShort => 'nada lo bloquea';

  @override
  String get teamAgentWorkBlockedShort => 'bloqueado';

  @override
  String get teamAgentStepCommand => 'Ejecutó un comando';

  @override
  String get teamAgentStepTest => 'Corrió las pruebas';

  @override
  String get teamAgentStepRead => 'Leyó un archivo';

  @override
  String get teamAgentStepEdit => 'Editó un archivo';

  @override
  String get teamAgentStepSearch => 'Buscó en el código';

  @override
  String teamAgentStepTool(String tool) {
    return 'Usó $tool';
  }

  @override
  String get teamAgentLastCommandLabel => 'Último comando';

  @override
  String get monitorBackgroundChecks => 'Revisiones en segundo plano';

  @override
  String get settingsTryDemo => 'Probar el demo';

  @override
  String get searchArchivedConversations => 'Conversaciones archivadas';

  @override
  String get readAloudConsentEngine =>
      'Solo se ofrecen voces sin conexión, pero el motor de voz es software aparte con sus propios términos de privacidad.';

  @override
  String get readAloudConsentHeard =>
      'Las personas cerca pueden oírla. La lectura se detiene cuando sales de esta conversación o de la app.';

  @override
  String get transcriptFindStopSearchingAll =>
      'Dejar de buscar en mensajes anteriores';

  @override
  String nudgeReviewChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'OpenCode cambió $count archivos. Revísalos antes de seguir.',
      one: 'OpenCode cambió 1 archivo. Revísalo antes de seguir.',
    );
    return '$_temp0';
  }

  @override
  String get voiceComponentTitle => 'Escritura por voz';

  @override
  String get voiceComponentSummary =>
      'Habla en lugar de escribir, incluso sin conexión';

  @override
  String get voiceComponentRemove => 'Quitar la escritura por voz';

  @override
  String get voiceComponentRemoveTitle => '¿Quitar la escritura por voz?';

  @override
  String voiceComponentRemoveBody(String size) {
    return 'Elimina el modelo de voz y libera $size. La escritura por voz dejará de funcionar hasta que la vuelvas a agregar aquí.';
  }

  @override
  String get setupAppStageDownloading => 'Descargando';

  @override
  String get setupAppStageVerifying => 'Comprobando la descarga';

  @override
  String kitDiffFilePosition(int index, int count) {
    return '$index de $count';
  }

  @override
  String kitDiffFilePositionSpoken(int index, int count) {
    return 'archivo $index de $count';
  }

  @override
  String get kitDiffViewed => 'Visto';

  @override
  String get kitDiffSelectHunk => 'Seleccionar estas líneas';

  @override
  String get kitCapFlagTerminalTitle => 'Terminal';

  @override
  String get kitCapFlagTerminalWhy =>
      'Este servidor no abre una terminal para ti.';

  @override
  String get kitCapFlagToolInventoryTitle => 'Lista de herramientas';

  @override
  String get kitCapFlagToolInventoryWhy =>
      'Este servidor no lista las herramientas que su agente puede usar.';

  @override
  String get demoScreenReset => 'Reiniciar demo';

  @override
  String get demoScreenLeave => 'Salir de la demo';

  @override
  String get demoScreenDisclosure =>
      'Todo aquí es simulado en este dispositivo. No se accede a ningún servidor, proveedor ni archivo.';

  @override
  String capabilityScreenIntroWithGaps(String server) {
    return '$server decide qué aparece en esta app. Lo que no puede hacer se queda fuera de los menús y las pestañas, en vez de mostrarse en gris. Las funciones faltantes funcionan en otros servidores de OpenCode.';
  }

  @override
  String activeContextMessageTitle(String role) {
    String _temp0 = intl.Intl.selectLogic(role, {
      'user': 'Mensaje del usuario',
      'assistant': 'Mensaje del asistente',
      'system': 'Mensaje del sistema',
      'synthetic': 'Mensaje sintético',
      'skill': 'Mensaje de skill',
      'shell': 'Mensaje de shell',
      'compaction': 'Mensaje de resumen',
      'change': 'Cambio de conversación',
      'other': 'Mensaje',
    });
    return '$_temp0';
  }

  @override
  String get chatDraftCopy => 'Copiar borrador';

  @override
  String get reportProblemDescribeLabel => '¿Qué ocurrió?';

  @override
  String get reportProblemDescribeHint =>
      'Qué hiciste, qué esperabas, qué obtuviste en su lugar';

  @override
  String get reportProblemDescribeFirst => 'Di primero qué ocurrió';

  @override
  String reportProblemErrorsLabel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count errores recientes',
      one: '1 error reciente',
    );
    return '$_temp0';
  }

  @override
  String get reportProblemClearFailed =>
      'No se pudo borrar el informe guardado. Reintenta.';

  @override
  String get reportProblemLinkCopiesDiagnostics =>
      'Los diagnósticos son demasiado largos para el enlace. Al abrir el formulario se copian, así que pégalos en su campo Diagnósticos.';

  @override
  String get reportProblemLinkCopiesWhole =>
      'El informe es demasiado largo para el enlace. Al abrir el formulario se copia, así que pégalo en el formulario.';

  @override
  String get reportProblemCopied => 'Informe copiado';

  @override
  String get reportProblemDiagnosticsCopied =>
      'Diagnósticos copiados: pégalos en el formulario';

  @override
  String reportProblemErrorBadge(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count errores guardados',
      one: '1 error guardado',
    );
    return '$_temp0';
  }

  @override
  String get thisPhoneAddToolsDetail =>
      'Python, AI Team, dictado por voz y más';

  @override
  String get undoFromHereNowAction => 'Deshacer ahora';

  @override
  String undoFromHereBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Este mensaje y los $count mensajes posteriores se eliminan y los archivos vuelven a como estaban antes de él. Puedes devolverlos hasta que envíes otro mensaje.',
      one:
          'Este mensaje y el mensaje posterior se eliminan y los archivos vuelven a como estaban antes de él. Puedes devolverlos hasta que envíes otro mensaje.',
      zero:
          'Este mensaje se elimina y los archivos vuelven a como estaban antes de él. Puedes devolverlo hasta que envíes otro mensaje.',
    );
    return '$_temp0';
  }

  @override
  String get undoFromHereBodyUnknown =>
      'Este mensaje y todo lo que viene después se eliminan y los archivos vuelven a como estaban antes de él. Puedes devolverlos hasta que envíes otro mensaje.';

  @override
  String get undoFromHereFilesLabel =>
      'Archivos que el agente editó después de él';

  @override
  String get undoFromHereNoEdits =>
      'El agente no reportó ediciones de archivos después de este mensaje.';

  @override
  String get undoneStatus => 'Deshecho desde un mensaje';

  @override
  String get undonePutBack => 'Restaurar';

  @override
  String reviewRevertScreenIntroCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Este mensaje y los $count mensajes posteriores están ocultos. Nada es definitivo hasta que elijas abajo.',
      one:
          'Este mensaje y el mensaje posterior están ocultos. Nada es definitivo hasta que elijas abajo.',
      zero:
          'Este mensaje está oculto; nada vino después de él. Nada es definitivo hasta que elijas abajo.',
    );
    return '$_temp0';
  }

  @override
  String reviewRevertKeepConsequenceCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'El mensaje oculto y los $count mensajes posteriores se eliminan',
      one: 'El mensaje oculto y el mensaje posterior se eliminan',
      zero: 'El mensaje oculto se elimina',
    );
    return '$_temp0';
  }

  @override
  String addServerConnectedHost(String host) {
    return 'Conectado a $host';
  }

  @override
  String addServerCheckSlow(String host) {
    return '$host aún no ha respondido. Una red lenta puede tardar un poco.';
  }

  @override
  String get addServerCheckCancel => 'Dejar de comprobar';

  @override
  String get addServerUseTailscale => 'Usar Tailscale';

  @override
  String get addServerStepsLabel => 'Progreso para agregar servidor';

  @override
  String get addServerStepKind => 'Qué se ejecuta allí';

  @override
  String get addServerStepTailscale => 'Tailscale en este teléfono';

  @override
  String get addServerStepPair => 'Empareja o introduce la dirección';

  @override
  String get addServerStepAddress => 'Dirección e inicio de sesión';

  @override
  String get addServerStepCheck => 'Comprobando';

  @override
  String get addServerStepReady => 'Listo';

  @override
  String addServerReadyTitle(String name) {
    return '$name está conectado';
  }

  @override
  String get addServerReadyBody =>
      'Sus conversaciones se abren a continuación. Empieza una o retoma una que ya esté ahí.';

  @override
  String addServerReadyOpen(String name) {
    return 'Abrir $name';
  }

  @override
  String get handoffUiLinkAddTitle => '¿Agregar este servidor?';

  @override
  String get handoffUiLinkAddServer => 'Agregar el servidor';

  @override
  String get failedJobReport => 'Reportar este fallo';

  @override
  String get sessionsOlderLoadFailed =>
      'No se pudieron cargar las conversaciones más viejas.';

  @override
  String get sessionsLoadFailed => 'No se pudieron cargar tus conversaciones.';

  @override
  String get sessionsListChanged =>
      'La lista de conversaciones cambió en el servidor. Actualízala para ver las conversaciones más viejas.';

  @override
  String get handoffUiComputerUnsupported =>
      'Este servidor no puede dar un comando que continúe una conversación en una computadora.';

  @override
  String get handoffUiComputerChanged =>
      'Esta conversación se movió o su servidor cambió. Vuelve y reintenta.';

  @override
  String commandAuthSheetIntro(String provider) {
    return 'Corre este inicio de sesión en tu servidor, no en este teléfono. Inícialo solo si confías en el servidor y en $provider. Quizá tengas que completar pasos en el servidor.';
  }

  @override
  String commandAuthCheckNamed(String provider) {
    return 'Revisar el inicio de sesión de $provider ahora';
  }

  @override
  String credentialRemoveAccountTitle(String provider, String name) {
    return '¿Quitar la cuenta “$name” de $provider?';
  }

  @override
  String credentialRemoveConfirmNamed(String name) {
    return 'Quitar “$name”';
  }

  @override
  String get discoverServicesAliases =>
      'servicios servidor de desarrollo vista previa logs comandos de corrida procesos';

  @override
  String get discoverCloudEnvironmentsAliases =>
      'entornos en la nube entornos gestionados sandbox remoto';

  @override
  String promptRestoredWithout(String names) {
    return 'Restaurado sin $names; adjúntalos otra vez antes de enviar';
  }

  @override
  String get promptStashOlderDraftsWaiting =>
      'Algunos borradores más viejos aún no se han movido aquí. Se conservan en este dispositivo.';

  @override
  String get promptStashOlderDraftsFull =>
      'Borradores más viejos esperan por moverse aquí. Elimina mensajes guardados para hacer espacio.';

  @override
  String get quotaCollectorGuide => 'Abrir la guía del recolector';

  @override
  String get usageSpentToday => 'Gastado hoy';

  @override
  String get usageSpentThirtyDays => 'Gastado en los últimos 30 días';

  @override
  String get usageSpentYear => 'Gastado este año';

  @override
  String get usageSpentAllTime => 'Gastado en total';

  @override
  String usageSpentPeriod(String period) {
    return 'Gastado · $period';
  }

  @override
  String get automationTitle => 'Lo que corre por sí solo';

  @override
  String get automationSearchAliases =>
      'automatización automático supervisión autoaprobar aprobaciones permitir siempre permisos fondo vigilar monitorear nivel del equipo';

  @override
  String get automationSaveFailed =>
      'Esta elección no se guardó en este teléfono. El nivel de arriba sigue siendo el en uso; reintenta.';

  @override
  String get automationSaving => 'Guardando…';

  @override
  String get automationTeamLabel => 'Cuánto decide solo el AI Team';

  @override
  String get automationTeamFootnote =>
      'Las tareas nuevas del equipo empiezan en este nivel. Puedes elegir otro nivel para una tarea al iniciarla.';

  @override
  String get automationWithoutAskingLabel => 'Sin preguntarte';

  @override
  String get automationSavedRulesDetail =>
      'Lo que el agente puede correr aquí sin preguntarte.';

  @override
  String get teamPhoneReadyChooseTitle => 'Elige el proyecto del equipo';

  @override
  String get teamPhoneReadyTurningOnTitle => 'Activando AI Team';

  @override
  String get teamPhoneReadyFailedTitle => 'AI Team no inició';

  @override
  String teamPhoneReadyBody(String project) {
    return 'Dale una primera tarea. Planifica el trabajo, lo reparte entre sus agentes y trae el resultado de vuelta a $project.';
  }

  @override
  String get teamPhoneReadyFirstTask => 'Darle una primera tarea al equipo';

  @override
  String get teamUiStateNotAnsweringPhone =>
      'La app sigue intentando mientras el equipo arranca en este teléfono.';

  @override
  String get teamUiStateNotAnsweringComputer =>
      'La app sigue intentando. Revisa que tu computadora esté encendida y en línea.';

  @override
  String teamUiStateNotAnsweringComputerNamed(String computer) {
    return 'La app sigue intentando. Revisa que $computer esté encendida y en línea.';
  }

  @override
  String get teamHomeChangeAddress => 'Cambiar la dirección';

  @override
  String get teamHomeTurnOffFailed =>
      'No se pudo detener el equipo en este teléfono, así que sigue encendido. Reintenta.';

  @override
  String get teamHomeHostCooling => 'Enfriándose';

  @override
  String get teamHomeHostStoppedForHeat => 'Detenido para enfriarse';

  @override
  String teamHomeHeatPausedLine(String time) {
    return 'El teléfono se calentó a las $time, así que el equipo se pausó. Sigue solo una vez que el teléfono se enfríe.';
  }

  @override
  String teamHomeHeatStoppedLine(String time) {
    return 'El teléfono se calentó mucho a las $time, así que el equipo se detuvo. Su trabajo se conserva y arranca otra vez cuando el teléfono se enfríe.';
  }

  @override
  String teamHomeSpentToday(String usage) {
    return 'Hoy · $usage';
  }

  @override
  String get teamHomeSpentHint =>
      'Todo el equipo desde la medianoche donde corre, estimado. El servidor no reporta cuánto costó cada tarea.';

  @override
  String get teamHomeSpentPartial =>
      'Parte del uso de hoy aún no tiene precio, así que costó más que esto.';

  @override
  String teamIntroNotFound(String server) {
    return 'No se encontró AI Team en $server';
  }

  @override
  String get chatErrorModelNotFound => 'El servidor no tiene este modelo.';

  @override
  String get chatErrorContextOverflow =>
      'Esta conversación es demasiado larga para el modelo.';

  @override
  String get chatErrorProviderAuth =>
      'El proveedor del modelo necesita que vuelvas a iniciar sesión.';

  @override
  String get chatErrorOutputLength =>
      'La respuesta alcanzó el límite de longitud del modelo.';

  @override
  String get chatErrorContentFilter =>
      'El filtro de seguridad del proveedor detuvo esta respuesta.';

  @override
  String get chatErrorUnknown => 'El agente se detuvo por un error.';

  @override
  String modelPickerThinkingChip(String level) {
    return 'Razonamiento: $level';
  }

  @override
  String modelPickerAgentChip(String agent) {
    return 'Agente: $agent';
  }

  @override
  String serversRemoveQueuedKept(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes en cola pasan a Mensajes guardados',
      one: '1 mensaje en cola pasa a Mensajes guardados',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedUncertain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Puede que $count ya se hayan enviado',
      one: 'Puede que 1 ya se haya enviado',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveDeleteQueued(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Quitar y eliminar $count mensajes en cola',
      one: 'Quitar y eliminar el mensaje en cola',
    );
    return '$_temp0';
  }

  @override
  String serversRemoveQueuedChanged(String name) {
    return 'Los mensajes en cola de $name cambiaron, así que no se quitó nada. Vuelve a quitarlo para ver el nuevo recuento.';
  }

  @override
  String serversRemoveQueuedNotKept(String name) {
    return 'No se pudieron mover los mensajes en cola de $name a Mensajes guardados, así que no se quitó nada. Elimina algunos mensajes guardados o libera almacenamiento e inténtalo de nuevo.';
  }

  @override
  String get settingsHubGroupAgent => 'Agente';

  @override
  String get settingsHubGroupConversations => 'Conversaciones';

  @override
  String get settingsHubGroupThisApp => 'Esta app';

  @override
  String get settingsHubProvidersRow => 'Proveedores y cuentas';

  @override
  String get settingsHubToolsRow => 'Herramientas';

  @override
  String get settingsHubShowReasoning => 'Mostrar razonamiento';

  @override
  String get settingsHubShowTimestamps => 'Mostrar hora, tokens y costo';

  @override
  String settingsHubUnavailableCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ajustes no están disponibles en este servidor',
      one: '1 ajuste no está disponible en este servidor',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubUnavailableWhy => 'Por qué';

  @override
  String get privacyPolicyTitle => 'Política de privacidad';

  @override
  String get aboutHelpSection => 'Consejos y atajos';

  @override
  String get settingsHubSearchToolsAliases =>
      'herramientas mcp integraciones comandos skills referencias barra capacidades plugins agentes externos a2a';

  @override
  String get aiteamComponentTurnOff => 'Apagar AI Team';

  @override
  String get aiteamComponentTurnOffTitle => '¿Apagar AI Team?';

  @override
  String get aiteamComponentTurnOffBody =>
      'El equipo se detiene y queda apagado hasta que lo actives otra vez.';

  @override
  String get aiteamComponentTurnOffKept =>
      'Sus tareas y ajustes, y tus proyectos, se quedan';

  @override
  String get aiteamComponentTurnOffFailed =>
      'No se pudo apagar AI Team. Reintenta, o reinicia la app.';

  @override
  String thisPhoneRemoveTool(String tool) {
    return 'Quitar $tool';
  }

  @override
  String thisPhoneRemoveToolTitle(String tool) {
    return '¿Quitar $tool?';
  }

  @override
  String thisPhoneRemoveToolBody(String size) {
    return 'Se devuelven cerca de $size. Puedes agregarlo otra vez desde Agregar herramientas.';
  }

  @override
  String get thisPhoneRemoveToolBodyUnmeasured =>
      'Puedes agregarlo otra vez desde Agregar herramientas.';

  @override
  String thisPhoneRemoveToolNeededBy(String tools) {
    return 'Lo necesita $tools';
  }

  @override
  String get thisPhoneRemoveToolDetail =>
      'Lo elimina de este teléfono. Tus proyectos se quedan.';

  @override
  String get thisPhoneRemovePythonDetail =>
      'Elimina pip y venv. Python y tus proyectos se quedan.';

  @override
  String get thisPhoneRemovePythonLost =>
      'pip y venv se eliminan, con los paquetes que solo ellos usaban';

  @override
  String get thisPhoneRemovePythonKept =>
      'Python mismo y tus proyectos se quedan';

  @override
  String get thisPhoneRemoveTeamDetail =>
      'Elimina los programas, tareas y ajustes del equipo. Los proyectos se quedan.';

  @override
  String get thisPhoneRemoveTeamLost =>
      'Los programas, tareas y ajustes del equipo se eliminan';

  @override
  String get thisPhoneRemoveTeamLostWork =>
      'El trabajo del equipo que aún no esté en tus proyectos se pierde';

  @override
  String get thisPhoneRemoveTeamKept =>
      'Tus archivos del proyecto y su historial de git se quedan';

  @override
  String get thisPhoneRemoveVoiceDetail =>
      'Elimina el modelo de voz. El dictado por voz se detiene hasta que lo agregues otra vez.';

  @override
  String get thisPhoneRemoveVoiceLost =>
      'El dictado por voz se detiene hasta que lo agregues otra vez';

  @override
  String get thisPhoneRemoveToolKept => 'Tus proyectos se quedan';

  @override
  String get removeFromPhoneKeepLost =>
      'Las conversaciones y los ajustes dentro de OpenCode se eliminan';

  @override
  String get removeFromPhoneKeepKept =>
      'Tus proyectos se conservan y vuelven cuando lo configures de nuevo';

  @override
  String removeFromPhoneKeepKeptSize(String size) {
    return 'Tus proyectos ($size) se conservan y vuelven cuando lo configures de nuevo';
  }

  @override
  String get removeFromPhoneDeleteLost =>
      'Los archivos de proyecto que no estén guardados en otro lugar se pierden para siempre';

  @override
  String get productErrorTimedOut =>
      'El servidor tardó demasiado en responder. Inténtalo de nuevo.';

  @override
  String get productErrorCertificate =>
      'El certificado de seguridad del servidor no es de confianza, así que la app se detuvo. Revisa la dirección del servidor.';

  @override
  String get productErrorSignIn =>
      'El servidor no aceptó el inicio de sesión. Revisa la contraseña en los ajustes del servidor.';

  @override
  String get productErrorNotFound =>
      'El servidor no lo encontró. Puede que se haya movido o eliminado.';

  @override
  String get productErrorConflict =>
      'Mientras tanto cambió en el servidor. Actualiza e inténtalo de nuevo.';

  @override
  String get productErrorBusy =>
      'El servidor está ocupado. Espera un momento e inténtalo de nuevo.';

  @override
  String get productErrorRejected =>
      'El servidor no aceptó la solicitud. Inténtalo de nuevo o informa del problema.';

  @override
  String get productErrorUnknown =>
      'Eso no funcionó. Detalles muestra qué pasó. Inténtalo de nuevo o informa del problema.';

  @override
  String get productErrorUnexpected =>
      'La app no entendió la respuesta del servidor. Inténtalo de nuevo o informa del problema.';

  @override
  String get productErrorDevice =>
      'Algo en este dispositivo no funcionó. Inténtalo de nuevo.';

  @override
  String get productErrorStorage =>
      'La app no pudo leer ni guardar un archivo en este dispositivo.';

  @override
  String get productErrorDetailsLabel => 'Detalles del error';

  @override
  String productErrorServer(int code) {
    return 'El servidor tuvo un problema (error $code). Inténtalo de nuevo en un momento.';
  }

  @override
  String get usageBudgetInvalidUsd => 'Escribe un monto mayor que 0, como 2.50';

  @override
  String get usageBudgetInvalidTokens =>
      'Escribe un número entero de tokens mayor que 0';

  @override
  String get usageBudgetSaveUsd => 'Guardar el presupuesto en USD';

  @override
  String get usageBudgetSaveTokens => 'Guardar el presupuesto de tokens';

  @override
  String sessionDestinationMoveWithChanges(String destination) {
    return 'Mover a $destination con cambios';
  }

  @override
  String sessionDestinationWarpWithChanges(String destination) {
    return 'Mover a $destination con una copia de los cambios';
  }

  @override
  String sessionDestinationMoveTo(String destination) {
    return 'Mover a $destination';
  }

  @override
  String sessionDestinationNoChanges(String place) {
    return 'No hay cambios en trabajo en $place, así que solo se mueve la conversación.';
  }

  @override
  String get settingsBackgroundOffFailed =>
      'Android no desactivó el modo en segundo plano.';

  @override
  String defaultReviewScopeNotice(String scope) {
    return 'Mostrando $scope: es la vista con cambios.';
  }

  @override
  String defaultModelNotice(String model) {
    return 'Usando $model, el modelo por defecto de este servidor.';
  }

  @override
  String get defaultModelChange => 'Elegir otro modelo';

  @override
  String teamControlReceiptSending(String control) {
    return '$control · Enviando…';
  }

  @override
  String get teamGateCardRunFailedOpen => 'Elige qué hacer';

  @override
  String get teamGateCardIfIgnored =>
      'El equipo espera hasta que respondas. Nada se pierde.';

  @override
  String get teamGateCardIfIgnoredFailed =>
      'La tarea queda detenida hasta que alguien actúe sobre ella.';

  @override
  String get teamGateCardIfIgnoredReview =>
      'El trabajo espera revisión. Nada se pierde.';

  @override
  String get termuxProcsBusy => 'Ocupado';

  @override
  String get termuxProcsIdle => 'Inactivo';

  @override
  String termuxProcsRunningFor(String elapsed) {
    return 'corriendo desde hace $elapsed';
  }

  @override
  String get termuxProcsCpuTime => 'Tiempo de procesador';

  @override
  String get consentBatteryTitle => '¿Mantener el servidor funcionando?';

  @override
  String get consentBatteryBody =>
      'Android puede detener el servidor de este teléfono mientras la app está cerrada. Permite el funcionamiento en segundo plano y Android te pedirá confirmarlo.';

  @override
  String get consentBatteryAllow =>
      'Permitir el funcionamiento en segundo plano';

  @override
  String get consentMakerTitle => '¿Reiniciar el servidor automáticamente?';

  @override
  String consentMakerBody(String maker) {
    return 'Los teléfonos $maker detienen las apps que no tienen permitido arrancar solas, y entonces el servidor queda apagado. Activa el auto-arranque de esta app en la pantalla que se abre.';
  }

  @override
  String get consentMakerBodyUnnamed =>
      'Algunos teléfonos detienen las apps que no tienen permitido arrancar solas, y entonces el servidor queda apagado. Activa el auto-arranque de esta app en la pantalla que se abre.';

  @override
  String get consentMakerAllow => 'Abrir los ajustes de auto-arranque';

  @override
  String get consentNotNow => 'Ahora no';

  @override
  String get consentSaveFailed =>
      'Tu respuesta no se pudo guardar en este teléfono, así que no se cambió nada. Inténtalo de nuevo.';

  @override
  String get consentStorageFailed =>
      'No se pudieron leer tus respuestas anteriores en este servidor, así que la app no las volverá a preguntar por ahora. Abre esta página de nuevo para reintentar.';

  @override
  String get consentGroupLabel => 'Tus respuestas';

  @override
  String get consentRowBattery => 'Funcionamiento en segundo plano';

  @override
  String get consentRowMaker => 'Arrancar solo otra vez';

  @override
  String get consentRowNeedsYou => 'Avísame cuando el agente me necesite';

  @override
  String get consentRowAlwaysAllow => 'Ofertas de Permitir siempre';

  @override
  String get consentWhyBattery =>
      'Android puede detener el servidor de este teléfono mientras la app está cerrada.';

  @override
  String get consentWhyMaker =>
      'Este teléfono puede no volver a arrancar el servidor después de que se detenga.';

  @override
  String get consentWhyNeedsYou =>
      'No recibirás una notificación cuando el agente espere tu respuesta.';

  @override
  String get consentAllowedSystem =>
      'Lo decide el ajuste propio del teléfono. Toca para revisarlo o apagarlo.';

  @override
  String get consentAllowedNeedsYou => 'Toca para cambiarlo en Notificaciones.';

  @override
  String get consentWhyAlwaysAllow =>
      'Se pregunta cada vez. Toca para que vuelvan las ofertas de Permitir siempre.';

  @override
  String get consentValueAllowed => 'Permitido';

  @override
  String get consentValueDeclined => 'Rechazado';

  @override
  String get consentValueUnanswered => 'Sin responder';

  @override
  String consentValueDeclinedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count rechazados',
      one: '1 rechazado',
    );
    return '$_temp0';
  }

  @override
  String get consentAlwaysAgainTitle => '¿Pedir permitir siempre?';

  @override
  String get consentAlwaysAgainBody =>
      'Después de 3 preguntas idénticas más, la app ofrecerá permitir siempre. Nada se permite hasta que tú lo digas.';

  @override
  String get consentAlwaysAgainConfirm => 'Ofrecer otra vez';

  @override
  String consentAlwaysAllowQuestion(String what) {
    return 'Preguntado 3 veces. ¿Permitir siempre $what?';
  }

  @override
  String get consentAlwaysAllowDecline => 'Seguir preguntando';

  @override
  String get consentAlwaysAllowFailed =>
      'El servidor no guardó Permitir siempre. La solicitud sigue esperando; inténtalo de nuevo o respóndela solo esta vez.';

  @override
  String get consentAlwaysAllowTitle => '¿Permitir siempre esta solicitud?';

  @override
  String get consentNeedsYouAllow => 'Activar las notificaciones';

  @override
  String get bootstrapOpeningTitle => 'Abriendo…';

  @override
  String get bootstrapOpeningBody => 'Leyendo tus servidores guardados.';

  @override
  String get bootstrapFailedTitle =>
      'No se pueden leer los servidores guardados';

  @override
  String get bootstrapFailedBody =>
      'Si tu teléfono acaba de reiniciarse, desbloquéalo e inténtalo de nuevo.';

  @override
  String get shareFailedLine =>
      'Texto compartido guardado · no se pudo abrir una conversación';

  @override
  String get shareFailedAgainLine =>
      'Aún no se pudo abrir una conversación · texto compartido guardado';

  @override
  String get shareFailedCopy => 'Copiar el texto compartido';

  @override
  String get shareFailedDiscard => 'Descartar el texto compartido';

  @override
  String get shareDiscarded => 'Texto compartido descartado';

  @override
  String get shareConnectionChanged =>
      'El servidor o el proyecto cambió mientras se abría. Reintenta.';

  @override
  String get appNewConversationFailed =>
      'No se pudo iniciar una conversación nueva';

  @override
  String get connectionFailureLocalCodexBody =>
      'Un listener local de Codex debería responder en este teléfono, pero no respondió nada.';

  @override
  String get connectionFailureRemoteCodexBody =>
      'Nadie respondió en el endpoint de Codex.';

  @override
  String get connectionFailureLoopbackBody =>
      'La app buscó un servidor en este teléfono y no obtuvo respuesta. Inicia ese servidor o reconecta el túnel que trae uno hasta aquí, y vuelve a intentarlo.';

  @override
  String get connectionFailureTimedOutBody =>
      'Hay algo en esa dirección, pero no respondió. Suele ser la red intermedia, no el servidor.';

  @override
  String get connectionFailureNothingAnsweredBody =>
      'Nadie respondió. O el servidor no está en ejecución, o este teléfono no puede llegar a su dirección.';

  @override
  String get connectionFailureUnknownBody =>
      'La conexión falló. Lo que salió mal está en Detalles.';

  @override
  String get connectionFailureTailnetCheck =>
      'Esta es una dirección de Tailscale: ¿está activado Tailscale en este teléfono y en el servidor?';

  @override
  String get teamHomeSpentHistoryMissing =>
      'Parte del historial de hoy falta, así que costó más que esto.';

  @override
  String get teamHomeSpentNotRecording =>
      'El equipo no está contando el uso nuevo en este momento.';

  @override
  String get teamRunCostUnreported =>
      'No reportado por tarea. La página de AI Team muestra el estimado de hoy para todo el equipo.';

  @override
  String get teamHomeUpkeepTitle => 'Mantenimiento del equipo';

  @override
  String get teamHomeUpkeepPatrol => 'Patrulla';

  @override
  String get teamHomeUpkeepChore => 'Tarea de mantenimiento';

  @override
  String teamHomeUpkeepGroup(int count, String kind, String state) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$kind ×$count · $state',
      one: '$kind · $state',
    );
    return '$_temp0';
  }

  @override
  String get teamAgentLooksAfterTeam => 'todo el equipo';

  @override
  String get teamAgentLooksAfterWatchdog => 'vigilante';

  @override
  String get teamAgentLooksAfterWorkers => 'ejecutores';

  @override
  String get servicesStopConfirm => 'Detener el servicio';

  @override
  String get servicesRestartConfirm => 'Reiniciar el servicio';

  @override
  String get managedWorkspacesRemoveConfirm => 'Quitar el entorno';

  @override
  String managedWorkspacesCreateIn(String provider) {
    return 'En $provider';
  }

  @override
  String get voiceAutoSetupTitle => 'Dictado por voz';

  @override
  String get voiceAutoSetupChecking =>
      'Revisando lo que este teléfono puede correr';

  @override
  String get voiceAutoSetupOffer =>
      'Habla en lugar de teclear. La voz se vuelve texto en este teléfono, incluso sin conexión, y el audio nunca sale de él. Requiere una descarga única.';

  @override
  String voiceAutoSetupPicked(String model) {
    return 'Modelo de voz $model, elegido para la memoria de este teléfono';
  }

  @override
  String get voiceAutoSetupMobileData =>
      'Estás en datos móviles. Esta descarga cuenta en tu plan de datos.';

  @override
  String voiceAutoSetupDownload(String size) {
    return 'Descargar $size';
  }

  @override
  String voiceAutoSetupDownloadMobile(String size) {
    return 'Descargar $size con datos móviles';
  }

  @override
  String get voiceAutoSetupOtherModel => 'Elegir otro modelo de voz';

  @override
  String get voiceAutoSetupNotified =>
      'El progreso también aparece en tus notificaciones. La escucha empieza cuando termine.';

  @override
  String get voiceAutoSetupStartsAfter => 'La escucha empieza cuando termine.';

  @override
  String get voiceAutoSetupReady => 'El modelo de voz está en este teléfono.';

  @override
  String get voiceAutoSetupChooseModel => 'Elegir un modelo de voz';

  @override
  String get voiceAutoSetupUnknownMemory =>
      'Este teléfono no dijo cuánta memoria tiene, así que no se eligió ningún modelo de voz.';

  @override
  String get voiceAutoSetupOffline =>
      'Sin conexión a internet. Conéctate y reintenta.';

  @override
  String get voiceAutoSetupBusy => 'Ya se está descargando un modelo de voz.';

  @override
  String get voiceAutoSetupShowDownload => 'Mostrar la descarga';

  @override
  String get voiceAutoSetupNoCapture =>
      'Este teléfono no puede grabar la voz para el dictado.';

  @override
  String get voiceAutoSetupDetailFiles => 'Archivos';

  @override
  String get voiceAutoSetupDetailSize => 'Tamaño exacto';

  @override
  String get voiceAutoSetupDetailMemory => 'Memoria';

  @override
  String voiceAutoSetupDetailMemoryValue(int required, int available) {
    return 'Necesita $required MB; este teléfono tiene $available MB';
  }

  @override
  String get terminalScreenEmptyTitle => 'Aún no hay terminales';

  @override
  String terminalScreenEmptyBody(String project) {
    return 'Inicia uno en $project.';
  }

  @override
  String get terminalScreenEmptyBodyNoProject => 'Inicia uno en este proyecto.';

  @override
  String get integrationsProvidersExplanation =>
      'Los proveedores de modelos que este servidor puede usar. Conecta uno para empezar a conversar.';

  @override
  String get integrationsResourcesExplanation =>
      'Archivos y datos que los servidores MCP conectados le dan al agente.';

  @override
  String externalAgentsStopTaskTitle(String task) {
    return '¿Detener “$task”?';
  }

  @override
  String externalAgentsStopTaskConfirm(String agent) {
    return 'Pedirle a $agent detener';
  }

  @override
  String get externalAgentsStopTaskKeep => 'Seguir corriendo';

  @override
  String toolsDetailMenu(String tool) {
    return 'Acciones de $tool';
  }

  @override
  String kitDurationHours(int hours) {
    return '$hours h';
  }

  @override
  String kitDurationHoursMinutes(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String kitDurationDays(int days) {
    return '$days d';
  }

  @override
  String kitDurationDaysHours(int days, int hours) {
    return '$days d $hours h';
  }

  @override
  String kitToolFor(String duration) {
    return 'durante $duration';
  }

  @override
  String kitSinceWaitingForLong(String duration) {
    return 'Esperando $duration';
  }

  @override
  String get teamChatLeadRoutedIt => 'Se lo envió a los ejecutores';

  @override
  String get teamChatLeadStartingIt => 'El ejecutor arrancó';

  @override
  String get teamChatLeadClaimedWorkerIt => 'El ejecutor tomó la tarea';

  @override
  String get teamChatLeadPushedIt => 'Sus cambios están en una rama';

  @override
  String get teamChatLeadReviewIt => 'Pasó a revisión';

  @override
  String get teamChatLeadMergedIt => 'Se fusionó';

  @override
  String get teamChatLeadStepFailedIt => 'Falló';

  @override
  String get teamChatLeadStepCancelledIt => 'Se canceló';

  @override
  String teamChatNowNoProgress(String elapsed) {
    return 'Sin progreso desde hace $elapsed';
  }

  @override
  String teamChatNoProgressBody(String name, String time) {
    return '$name no ha movido esta tarea desde las $time. Dale un empujón para seguir, reiníciala, o reporta el problema.';
  }

  @override
  String teamChatNoProgressBodyNoControls(String name, String time) {
    return '$name no ha movido esta tarea desde las $time. Este servidor no puede darle un empujón ni reiniciarla desde aquí; reporta el problema o revisa la computadora del equipo.';
  }

  @override
  String get teamChatNoProgressReport => 'Reportar el problema';

  @override
  String teamChatNoProgressReportTitle(String elapsed) {
    return 'Sin progreso desde hace $elapsed';
  }

  @override
  String get teamTaskDetailsReported => 'Lo que el servidor reportó';

  @override
  String get kitToolOpenDetails => 'Abrir sus detalles';

  @override
  String get teamStartRunKeepInBacklog => 'Mantener en el Backlog';

  @override
  String get serverSettingsUpdateCommandsDetail =>
      'Corre los comandos en una terminal de la computadora del servidor; esta app no puede actualizarlo.';

  @override
  String get serverSettingsUpdateCommandsCopied =>
      'Copiado. Corre los comandos en una terminal de la computadora del servidor.';

  @override
  String hostServiceTitle(String server) {
    return 'OpenCode en $server';
  }

  @override
  String hostServiceIntro(String server) {
    return 'Estos comandos corren en la computadora de $server; copia cada uno en una terminal allí.';
  }

  @override
  String tailscaleSetupToDo(String detail) {
    return 'Pendiente · $detail';
  }

  @override
  String get tailscaleSetupNoDeviceList =>
      'OpenCode no puede listar los dispositivos de tu tailnet.';

  @override
  String get productErrorStagedRevert =>
      'Revisa la reversión preparada antes de enviar este mensaje en cola.';

  @override
  String teamWatchComposerHint(String name) {
    return 'Escribirle a $name…';
  }

  @override
  String teamWatchAboutRole(String role) {
    return 'Sobre $role';
  }

  @override
  String serversRemoveQueuedUnreadable(String name) {
    return 'No se pueden leer los mensajes en cola de $name. Se conservaron el servidor y sus mensajes en cola. Intenta quitarlo de nuevo cuando se pueda leer la cola.';
  }

  @override
  String get bootstrapStartFresh => 'Empezar de cero';

  @override
  String get bootstrapStartFreshTitle =>
      '¿Quitar los inicios de sesión guardados?';

  @override
  String get bootstrapStartFreshBody =>
      'Esto quita las contraseñas y los tokens de conexión guardados de este teléfono y borra el servidor seleccionado. Tus servidores guardados, mensajes en cola y borradores se conservan.';

  @override
  String get bootstrapStartFreshConfirm => 'Quitar inicios de sesión guardados';

  @override
  String get bootstrapResettingTitle => 'Quitando inicios de sesión guardados…';

  @override
  String get bootstrapResettingBody =>
      'Mantén la app abierta hasta que termine.';

  @override
  String get bootstrapResetFailedTitle =>
      'Falló el restablecimiento de sesiones';

  @override
  String get bootstrapResetFailedBody =>
      'No se pudieron quitar algunos inicios de sesión guardados. Inténtalo de nuevo.';

  @override
  String get workStalled => 'Estancado';

  @override
  String get teamNowActivityPlanning => 'Esperando un plan';

  @override
  String get teamNowActivityWaitingForWorker => 'Esperando a un ejecutor';

  @override
  String get teamNowActivityStartingWorker => 'Arrancando un ejecutor';

  @override
  String get teamNowActivityWorking => 'Trabajando en tu tarea';

  @override
  String get teamNowActivityReviewing => 'Revisando los cambios';

  @override
  String get teamNowActivityNeedsYou => 'Esperando tu respuesta';

  @override
  String get teamNowActivityDelayed => 'Tardando más de lo esperado';

  @override
  String get teamNowActivityUnconfirmed => 'Petición sin confirmar';

  @override
  String get teamNowActivityRefused => 'Petición no aceptada';

  @override
  String get teamNowActivityUnavailable => 'El equipo no responde';

  @override
  String get teamNowActivityCompleted => 'Terminado';

  @override
  String get teamNowActivityFailed => 'No pudo terminar';

  @override
  String get teamNowActivityCancelled => 'Detenido';

  @override
  String get teamNowReasonNoPlanReported =>
      'Aún no se ha reportado ningún plan. La razón es desconocida.';

  @override
  String get teamNowReasonNoWorkerReported =>
      'Aún no se ha reportado ningún ejecutor.';

  @override
  String get teamNowReasonWorkerStarting =>
      'El ejecutor arrancó, pero todavía no comienza la tarea.';

  @override
  String teamModelRowTitle(String model) {
    return 'Los ejecutores usan $model';
  }

  @override
  String get teamModelDefault => 'Igual que el OpenCode de este teléfono';

  @override
  String get teamModelDefaultHint =>
      'Usa el modelo configurado en el OpenCode de este teléfono.';

  @override
  String get teamModelChange =>
      'Cambiar. Entra en vigor la próxima vez que arranque un ejecutor.';

  @override
  String get teamModelSheetTitle => 'Modelo para los ejecutores';

  @override
  String get teamModelSheetNote =>
      'Solo modelos que el OpenCode de este teléfono puede usar. Un ejecutor que ya corre conserva su modelo.';

  @override
  String get teamModelNoneLoaded =>
      'Los modelos de este teléfono aún no cargan. Cierra esto y reintenta en un momento.';

  @override
  String get teamModelFailed =>
      'No se pudo cambiar el modelo. El equipo conserva el que tenía.';

  @override
  String get teamNowReasonWorkerPreparing =>
      'El ejecutor se prepara: se crea su carpeta y su programa arranca.';

  @override
  String get teamNowReasonWorkerRunning =>
      'El programa del ejecutor corre. El equipo aún no ha reportado que la tarea le llegó.';

  @override
  String get teamNowReasonWorkerTaskDelivered =>
      'La tarea llegó al ejecutor. La está leyendo antes de comenzar.';

  @override
  String teamNowLastStart(String duration) {
    return 'la última vez tomó $duration';
  }

  @override
  String get teamUiHostPhraseBusyStartingWorker =>
      'Ocupado arrancando un ejecutor';

  @override
  String get teamNowReasonWorkInProgress => 'La tarea está siendo trabajada.';

  @override
  String get teamNowReasonReviewPending =>
      'La revisión o la terminación sigue pendiente.';

  @override
  String get teamNowReasonAnswerNeeded => 'El equipo espera tu respuesta.';

  @override
  String get teamNowReasonWorkerCouldNotStart =>
      'El ejecutor no pudo mantenerse corriendo.';

  @override
  String get teamNowReasonProviderLimit =>
      'El servicio de IA reportó un límite de uso.';

  @override
  String get teamNowReasonWorkTakingLonger =>
      'El trabajo está tardando más de lo esperado.';

  @override
  String get teamNowReasonConfirmationMissing =>
      'No podemos confirmar que la petición llegó. Revisa antes de enviarla otra vez.';

  @override
  String get teamNowReasonRequestRefused => 'La petición no fue aceptada.';

  @override
  String get teamNowReasonConnectionUnavailable =>
      'No se puede revisar el progreso mientras no hay conexión.';

  @override
  String get teamNowReasonCauseUnknown =>
      'La razón es desconocida. Revisa lo que el equipo está haciendo.';

  @override
  String get teamNowWhyPlanning =>
      'El planificador convierte tu tarea en pasos. Esta conversación sigue la tarea apenas el equipo los liste. Dejar de seguirla aquí no la cancela en la computadora del equipo.';

  @override
  String get teamNowWhyWaitingForWorker =>
      'El equipo busca trabajo nuevo regularmente y arranca un ejecutor para él cuando hay uno libre.';

  @override
  String get teamNowWhyStartingWorker =>
      'Un ejecutor nuevo hace su propia copia del proyecto y arranca su programa antes de leer la tarea. Esa es la parte lenta en un teléfono, y la etapa de arriba es lo que el equipo reporta.';

  @override
  String get teamNowWhyWorking =>
      'El ejecutor hace los cambios en su propia copia y luego los entrega a revisión.';

  @override
  String get teamNowWhyReviewing =>
      'Un revisor revisa los cambios antes de fusionarse.';

  @override
  String get teamNowWhyUnconfirmed =>
      'La app envió la tarea pero no recibió respuesta. Enviarla otra vez podría iniciarla dos veces, así que revisa primero al planificador.';

  @override
  String get teamNowWhyWorkerCouldNotStart =>
      'El ejecutor se detuvo mientras arrancaba. Su conversación puede decir por qué.';

  @override
  String get teamNowWhyProviderLimit =>
      'El servicio de IA limita cuánto se puede usar en un periodo. El trabajo continúa cuando el límite se reajusta, o puedes detener la tarea.';

  @override
  String get teamNowWhyWorkTakingLonger =>
      'Las tareas grandes pueden tardar. Observar al ejecutor muestra si sigue moviéndose.';

  @override
  String get teamNowWhyCauseUnknown =>
      'Lo que el equipo reporta no dice por qué espera.';

  @override
  String get teamNowWhyHide => 'Ocultar';

  @override
  String get teamNowNextPlan => 'Después: el equipo lista los pasos';

  @override
  String get teamNowNextWorker => 'Después: arranca un ejecutor';

  @override
  String get teamNowNextWork => 'Después: el ejecutor comienza la tarea';

  @override
  String get teamNowNextReview => 'Después: se revisan los cambios';

  @override
  String get teamNowNextFinish => 'Después: la tarea termina';

  @override
  String get teamNowWatchPlanner => 'Observar al planificador';

  @override
  String get teamNowDismissRequest => 'Dejar de seguir esta petición';

  @override
  String teamNowUsuallyWithin(String duration) {
    return 'normalmente dentro de $duration';
  }

  @override
  String teamNowWatchAgent(String name) {
    return 'Observar a $name';
  }

  @override
  String get teamNowNotStartingLine =>
      'El equipo no está arrancando un ejecutor';

  @override
  String get aiSetupTitle => 'Configuración de IA';

  @override
  String get aiSetupEntryDetail =>
      'Modelos, herramientas y sugerencias para este servidor';

  @override
  String get aiSetupRefresh =>
      'Volver a leer la configuración de este servidor';

  @override
  String get aiSetupLoading => 'Leyendo la configuración de este servidor…';

  @override
  String get aiSetupReviewOnly =>
      'Solo lectura. Por ahora los cambios se hacen en el servidor.';

  @override
  String get aiSetupUnsupportedTitle =>
      'La configuración de IA no está disponible';

  @override
  String get aiSetupUnsupportedBody =>
      'Este servidor no comparte su configuración con la app. Configura sus modelos y herramientas en el propio servidor.';

  @override
  String get aiSetupSignInTitle => 'Hace falta iniciar sesión';

  @override
  String aiSetupSignInBody(String server) {
    return '$server no aceptó el inicio de sesión guardado, así que no se puede leer su configuración.';
  }

  @override
  String get aiSetupErrorTitle => 'No se pudo leer la configuración';

  @override
  String get aiSetupErrorBody =>
      'El servidor no respondió como se esperaba. Inténtalo de nuevo o revisa el servidor en su página de ajustes.';

  @override
  String get aiSetupTryAgain => 'Intentar de nuevo';

  @override
  String get aiSetupOfflineTitle => 'Estás sin conexión';

  @override
  String aiSetupOfflineBody(String server) {
    return 'Vuelve a conectarte a $server para leer su configuración.';
  }

  @override
  String aiSetupOfflineStale(String server) {
    return 'Sin conexión. Esta es la configuración de $server tal como se leyó por última vez; se actualiza al reconectar.';
  }

  @override
  String get aiSetupEmptyTitle => 'Aún no hay nada configurado';

  @override
  String get aiSetupEmptyBody =>
      'Este servidor usa sus valores predeterminados, sin modelo elegido ni servidores de herramientas. Por ahora los cambios se hacen en el servidor.';

  @override
  String get aiSetupSuggestionsLabel => 'Sugerencias';

  @override
  String aiSetupSuggestSignInTitle(String name) {
    return 'Inicia sesión en $name';
  }

  @override
  String get aiSetupSuggestSignInDetail =>
      'Sus herramientas permanecen desactivadas hasta que alguien inicie sesión en él en el servidor.';

  @override
  String aiSetupSuggestFixTitle(String name) {
    return 'Revisa los ajustes de $name';
  }

  @override
  String get aiSetupSuggestFixDetail =>
      'No se pudo iniciar. Corrige su entrada en la configuración del servidor y reinicia el servidor.';

  @override
  String get aiSetupSuggestModelTitle => 'Elige un modelo predeterminado';

  @override
  String get aiSetupSuggestModelDetail =>
      'No hay ningún modelo definido, así que las conversaciones nuevas usan el que elige el servidor. Define “model” en la configuración del servidor.';

  @override
  String get aiSetupSuggestToolsTitle => 'Agrega servidores de herramientas';

  @override
  String get aiSetupSuggestToolsDetail =>
      'No hay servidores MCP configurados. Agrega uno en la configuración del servidor para darle más herramientas al agente.';

  @override
  String get aiSetupToolsLabel => 'Servidores de herramientas';

  @override
  String get aiSetupToolsTerm =>
      'Los servidores MCP le dan herramientas adicionales al agente. Cada uno indica si funciona ahora.';

  @override
  String get aiSetupToolConnected => 'Conectado';

  @override
  String get aiSetupToolWaiting => 'En espera';

  @override
  String get aiSetupToolOff => 'Desactivado';

  @override
  String get aiSetupToolFailed => 'Falló';

  @override
  String get aiSetupToolNeedsSignIn => 'Requiere inicio de sesión';

  @override
  String get aiSetupToolUnknown => 'Desconocido';

  @override
  String get aiSetupEffectiveLabel => 'Ajustes en vigor';

  @override
  String get aiSetupEffectiveTerm =>
      'Lo que usan las conversaciones de este servidor, tras combinar sus archivos de configuración.';

  @override
  String get aiSetupModel => 'Modelo';

  @override
  String get aiSetupServerDefault => 'Sin definir: el servidor elige';

  @override
  String get aiSetupSmallModel => 'Modelo pequeño';

  @override
  String get aiSetupDefaultAgent => 'Agente predeterminado';

  @override
  String get aiSetupProviders => 'Proveedores';

  @override
  String get aiSetupPermissions => 'Permisos';

  @override
  String aiSetupPermissionRules(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reglas',
      one: '1 regla',
    );
    return '$_temp0';
  }

  @override
  String get aiSetupAllSettings => 'Todos los ajustes';

  @override
  String get aiSetupSourcesLabel => 'Fuentes de configuración';

  @override
  String get aiSetupSourcesTerm =>
      'Listadas de menor a mayor prioridad, tal como las informa el servidor. La app no las combina.';

  @override
  String get aiSetupNoSources => 'No hay archivos de configuración';

  @override
  String get aiSetupNoSourcesDetail =>
      'Este servidor usa sus valores predeterminados.';

  @override
  String aiSetupSourceUnnamed(String type) {
    return 'Fuente sin archivo ($type)';
  }

  @override
  String aiSetupSourceSets(int position, String keys) {
    return '$position. Define $keys';
  }

  @override
  String aiSetupSourceEmpty(int position) {
    return '$position. No define nada';
  }

  @override
  String get aiSetupAllSources => 'Todas las fuentes';

  @override
  String get integrationsPageLoadFailed => 'No se pudo cargar esta página';

  @override
  String kitLastKnownRefreshing(String updated) {
    return '$updated · Actualizando';
  }

  @override
  String get kitLastKnownHint =>
      'Guardado de la vez anterior. Se abren cuando cargue la lista en vivo.';

  @override
  String get kitTranscriptExcerptHint =>
      'Guardado de la vez anterior. La conversación se abre completa cuando termine de cargar.';

  @override
  String get lastKnownUpdatedJustNow => 'Actualizado hace un momento';

  @override
  String lastKnownUpdatedAgo(String ago) {
    return 'Actualizado hace $ago';
  }

  @override
  String serverRowQueuedWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes esperando para enviarse',
      one: '1 mensaje esperando para enviarse',
    );
    return '$_temp0';
  }

  @override
  String serverRowMoveQueued(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mover $count mensajes en espera a $destination',
      one: 'Mover 1 mensaje en espera a $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveTitle => 'Mover mensajes en fila';

  @override
  String queuedMoveSubtitle(String source) {
    return 'De $source';
  }

  @override
  String get queuedMovePromptsLabel => 'Mensajes';

  @override
  String get queuedMoveConversationLabel => 'Conversación';

  @override
  String get queuedMoveNewConversation => 'Conversación nueva';

  @override
  String queuedMoveQueuedAt(String time) {
    return 'En fila $time';
  }

  @override
  String queuedMoveFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos',
      one: '1 archivo',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveBlockedUncertain(String source) {
    return 'Puede que ya se haya enviado. Revísalo primero en $source.';
  }

  @override
  String queuedMoveBlockedFile(String source) {
    return 'Contiene un archivo que solo $source puede abrir';
  }

  @override
  String get queuedMoveBlockedMentions =>
      'Ocultar una contraseña dentro rompería sus menciones de agente';

  @override
  String queuedMoveHidesSecrets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Las contraseñas y claves de $count mensajes quedan ocultas',
      one: 'Las contraseñas y claves de 1 mensaje quedan ocultas',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUsesCurrentModel(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes usan el modelo elegido en $destination',
      one: '1 mensaje usa el modelo elegido en $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveAction(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mover $count mensajes a $destination',
      one: 'Mover 1 mensaje a $destination',
    );
    return '$_temp0';
  }

  @override
  String get queuedMoveChooseOne => 'Elige al menos un mensaje';

  @override
  String queuedMoveNoneLeft(String source) {
    return 'Ya nada espera a $source';
  }

  @override
  String queuedMoveFailedDisconnected(String destination) {
    return '$destination se desconectó, así que nada se movió. Conéctate a él y reintenta.';
  }

  @override
  String queuedMoveFailedConversationGone(String destination) {
    return 'Esa conversación ya no está en $destination, así que nada se movió. Elige otra.';
  }

  @override
  String queuedMoveFailedNothing(String source) {
    return 'Estos mensajes ya no esperan a $source, así que nada se movió.';
  }

  @override
  String queuedMoveFailedNewConversation(String destination) {
    return 'No se pudo empezar una conversación nueva en $destination, así que nada se movió. Reintenta o elige una conversación existente.';
  }

  @override
  String queuedMoveFailedNotSaved(String source) {
    return 'No se pudo guardar el movimiento, así que nada se movió. Los mensajes siguen esperando a $source.';
  }

  @override
  String queuedMoveDone(int count, String destination) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes movidos a $destination',
      one: '1 mensaje movido a $destination',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveDonePartial(int moved, int total, String destination) {
    return 'Se movieron $moved de $total mensajes a $destination. El resto ya no esperaba.';
  }

  @override
  String queuedMoveUndoNone(String destination) {
    return 'Los mensajes ya empezaron a enviarse en $destination, así que se quedan allí.';
  }

  @override
  String queuedMoveUndoPartial(int count, String destination, String source) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count mensajes ya empezaron a enviarse en $destination y se quedan allí. El resto vuelve a esperar a $source.',
      one:
          '1 mensaje ya empezó a enviarse en $destination y se queda allí. El resto vuelve a esperar a $source.',
    );
    return '$_temp0';
  }

  @override
  String queuedMoveUndoFailed(String destination) {
    return 'No se pudo devolver los mensajes. Se quedan en $destination.';
  }

  @override
  String get sessionAddressInclude => 'Incluir la dirección de este servidor';

  @override
  String get sessionAddressDisclosure =>
      'El enlace entonces muestra esta dirección y el ID de conversación, nunca una contraseña: el otro teléfono sigue necesitando su propio acceso. Capturas de pantalla, mensajes y el portapapeles pueden conservarla.';

  @override
  String get sessionAddressIntro =>
      'Escanea con OpenCode Mobile en el otro teléfono. El código lleva la dirección de este servidor y el ID de conversación.';

  @override
  String get sessionAddressUnsupportedHost =>
      'Solo una dirección HTTPS privada terminada en .ts.net puede ir en un enlace.';

  @override
  String get sessionAddressOpenTitle => 'Abrir una conversación compartida';

  @override
  String get sessionAddressConsentSaved => '¿Abrir en este servidor guardado?';

  @override
  String get sessionAddressConsentNew => '¿Agregar este servidor?';

  @override
  String get sessionAddressNotSaved => 'No guardado en este teléfono';

  @override
  String get sessionAddressConsentNote =>
      'El enlace no concede acceso. La revisión solo pregunta al servidor cuál instalación es; nada inicia sesión y no se envía ninguna contraseña.';

  @override
  String get sessionAddressCheck => 'Revisar el servidor';

  @override
  String sessionAddressChecking(String host) {
    return 'Revisando $host…';
  }

  @override
  String get sessionAddressAddBody =>
      'Este servidor no está guardado en este teléfono. Agrégalo con tu propio inicio de sesión; el enlace no lleva uno.';

  @override
  String get sessionAddressAddServer => 'Agregar el servidor';

  @override
  String get sessionAddressChooseBody =>
      'Más de un servidor guardado usa esta dirección. Elige en cuál abrir la conversación.';

  @override
  String sessionAddressVerifyBody(String name) {
    return 'Confirma que $name es el servidor del que vino este enlace. El teléfono lo recuerda para $name; no inicia sesión ni comparte una contraseña.';
  }

  @override
  String get sessionAddressVerify => 'Verificar el servidor';

  @override
  String sessionAddressReadyBody(String name) {
    return '$name coincide con este enlace.';
  }

  @override
  String sessionAddressSignInBody(String name) {
    return 'Inicia sesión primero en $name con tu propia cuenta y luego abre la conversación.';
  }

  @override
  String get sessionAddressSignIn => 'Iniciar sesión';

  @override
  String get sessionAddressOpen => 'Abrir la conversación';

  @override
  String get sessionAddressOpening => 'Abriendo la conversación…';

  @override
  String get sessionAddressReason => 'Razón';

  @override
  String get sessionAddressFailUnavailable =>
      'Los enlaces de conversación con dirección de servidor aún no están disponibles.';

  @override
  String get sessionAddressFailInvalidLink =>
      'Este enlace de conversación no es válido. Escanéalo o cópialo de nuevo.';

  @override
  String get sessionAddressFailTooLarge =>
      'Este enlace es demasiado largo. Pide al remitente un enlace nuevo.';

  @override
  String get sessionAddressFailCredentials =>
      'Este enlace contiene información privada de inicio de sesión y no se puede usar.';

  @override
  String get sessionAddressFailConsentRequired =>
      'Elige primero si incluir la dirección de este servidor.';

  @override
  String get sessionAddressFailPrivateRouteRequired =>
      'Este servidor no se puede alcanzar por la conexión privada requerida. Revisa tu conexión.';

  @override
  String get sessionAddressFailUnreachable =>
      'No se pudo alcanzar el servidor. Revisa tu conexión y reintenta.';

  @override
  String get sessionAddressFailTimedOut =>
      'El servidor no respondió a tiempo. Reintenta.';

  @override
  String get sessionAddressFailTlsRejected =>
      'No se pudo verificar la conexión segura del servidor, así que el enlace no se abrió.';

  @override
  String get sessionAddressFailRedirectsRejected =>
      'Este servidor intentó mandar la petición a otro lado. El enlace no se abrió.';

  @override
  String get sessionAddressFailAccessDenied =>
      'Tu acceso a este servidor o conversación fue rechazado.';

  @override
  String get sessionAddressFailInvalidDescriptor =>
      'Este servidor no dio la información necesaria para abrir este enlace.';

  @override
  String get sessionAddressFailInstanceMismatch =>
      'Este enlace y el servidor guardado no identifican la misma instalación.';

  @override
  String get sessionAddressFailBindingRequired =>
      'Verifica este servidor guardado antes de abrir la conversación.';

  @override
  String get sessionAddressFailAmbiguousProfile =>
      'Elige cuál servidor guardado usar.';

  @override
  String get sessionAddressFailProfileMissing =>
      'Este servidor guardado ya no está disponible.';

  @override
  String get sessionAddressFailStorage =>
      'No se pudo guardar ni leer la verificación del servidor. Reintenta después de reiniciar la app.';

  @override
  String get sessionAddressFailSignInRequired =>
      'Inicia sesión en este servidor con tu propia cuenta antes de continuar.';

  @override
  String get sessionAddressFailUnsafeLookup =>
      'Este servidor no ha sido verificado para enlaces privados de conversación.';

  @override
  String get sessionAddressFailSessionMissing =>
      'Esta conversación no está disponible en este servidor.';

  @override
  String get sessionAddressFailCancelled => 'Se canceló abrir este enlace.';

  @override
  String get removeFromPhoneDeleteAllChoice => 'Eliminar todo…';

  @override
  String removeFromPhoneDeleteAllChoiceSize(String size) {
    return 'Eliminar todo y liberar unos $size…';
  }

  @override
  String get phoneServerCardErrorDetail => 'Error';

  @override
  String get sessionMenuGoTo => 'Ir a';

  @override
  String get sessionMenuDo => 'Hacer';

  @override
  String get sessionMenuFind => 'Buscar';

  @override
  String get sessionMenuSubagents => 'Sub-agentes';

  @override
  String get sessionMenuDetails => 'Detalles';

  @override
  String get sessionMenuShareHint => 'Cualquiera con el enlace puede leerlo';

  @override
  String get sessionMenuStopSharingHint =>
      'El enlace público deja de funcionar';

  @override
  String get sessionMenuCompactHint =>
      'Lo resume para que el agente tenga espacio de nuevo';

  @override
  String get sessionMenuForkHint =>
      'Abre una copia que puedes llevar por otro camino';

  @override
  String get sessionMenuContinueComputerHint =>
      'Muestra el comando que lo retoma allí';

  @override
  String get sessionMenuContinuePhoneHint =>
      'Muestra un código que la app de ese teléfono abre';

  @override
  String get sessionMenuNeedsPrompt => 'Disponible después del primer mensaje';

  @override
  String commandSheetServerGroup(String server) {
    return 'Comandos de $server';
  }

  @override
  String commandSheetAgentMissingTitle(String agent) {
    return 'Comandos de $agent no disponibles';
  }

  @override
  String commandSheetAgentMissingWhy(String agent) {
    return '$agent aún no comparte sus propios comandos con la app, así que la app no puede listarlos, ejecutarlos ni ejecutar comandos de shell con !. Las acciones propias de la app siguen funcionando.';
  }

  @override
  String commandSheetAgentCommandNotSent(String command, String agent) {
    return '$command no se envió: $agent aún no comparte sus comandos con la app. Quita la / para enviarlo como mensaje.';
  }

  @override
  String commandSheetShellNotSent(String command, String agent) {
    return '$command no se envió: los comandos de shell no se pueden ejecutar en $agent desde la app. Quita la ! para enviarlo como mensaje.';
  }

  @override
  String get commandSheetShellDescription =>
      'O empieza un mensaje con ! para correrlo desde el compositor';

  @override
  String get commandSheetRetryDescription => 'Envía tu último mensaje otra vez';

  @override
  String get commandSheetNoteDescription =>
      'Una nota que el agente tiene presente en esta conversación';

  @override
  String get commandSheetApprovalsDescription =>
      'Lo que esta conversación puede hacer sin preguntar';

  @override
  String get commandSheetReloadDescription =>
      'Lee esta conversación del servidor otra vez';

  @override
  String get commandSheetLibrarySubtitle =>
      'Elige un comando y luego la conversación en la que corre';

  @override
  String get commandSheetAgentFallback => 'Este agente';

  @override
  String get commandSheetPlanDescription =>
      'Abre el último plan del agente en la conversación';

  @override
  String get chatUiSessionMenu => 'Menú de la conversación';

  @override
  String get commandsScreenLoadFailed => 'No se pudieron cargar los comandos';

  @override
  String get commandSheetSubtitleAppOnly =>
      'Ejecuta una de las acciones de la app en esta conversación';

  @override
  String get voiceModeMicAsk =>
      'El dictado por voz necesita el micrófono. Toca Permitir el micrófono y elige Permitir.';

  @override
  String get voiceModeMicAllow => 'Permitir el micrófono';

  @override
  String get voiceModeMicBlocked =>
      'Android bloquea el micrófono para esta app. Actívalo en los ajustes de Android y vuelve aquí.';

  @override
  String get voiceModeNothingHeard =>
      'No se escuchó nada. Toca el micrófono y reintenta.';

  @override
  String get teamDispatchCreating => 'Creando tu tarea…';

  @override
  String get teamDispatchSending => 'Tarea creada · enviándola al equipo…';

  @override
  String get teamDispatchAwaitingWorker =>
      'Tarea enviada al equipo · esperando a un ejecutor';

  @override
  String get teamDispatchWorkerStarted => 'Un ejecutor arrancó tu tarea';

  @override
  String get teamDispatchCreateRefused =>
      'La tarea no se creó. Cámbiala y envíala de nuevo.';

  @override
  String get teamDispatchAssignRefused =>
      'Tarea creada, pero no se pudo enviar al equipo';

  @override
  String get teamDispatchAssignRefusedHint =>
      'La tarea se queda en el tablero, sin asignar a nadie.';

  @override
  String get teamDispatchCreateUnconfirmed =>
      'No se pudo confirmar si la tarea se creó';

  @override
  String get teamDispatchDispatchUnconfirmed =>
      'Tarea creada · no se pudo confirmar que llegó al equipo';

  @override
  String get teamDispatchCheckBoard =>
      'Revisa el tablero antes de enviarla otra vez. Tus palabras se conservan.';

  @override
  String get teamDispatchUnknown =>
      'Tarea enviada · no se puede alcanzar al equipo, así que se desconoce si arrancó un ejecutor';

  @override
  String get teamDispatchCheckAgain => 'Revisar el equipo otra vez';

  @override
  String get teamDispatchTaskId => 'ID de la tarea';

  @override
  String get teamDispatchHostWords => 'La respuesta del equipo';

  @override
  String get teamUiHostGuideOpen => 'Abrir la guía completa';

  @override
  String get mcpAddBrowseTitle => 'Explorar el catálogo';

  @override
  String get mcpAddBrowseDetail =>
      'Servidores del registro público de MCP, activados con un interruptor';

  @override
  String get mcpAddBrowseNone =>
      'No hay catálogo para este servidor: no acepta servidores MCP nuevos desde la app.';

  @override
  String get mcpAddManualTitle => 'Escribir a mano';

  @override
  String get mcpAddManualDetail =>
      'Escribe su dirección, o el comando que lo arranca';

  @override
  String get mcpCatalogTitle => 'Catálogo de MCP';

  @override
  String get mcpCatalogConsentTitle => '¿Cargar el registro de MCP?';

  @override
  String get mcpCatalogConsentBody =>
      'La app pide a registry.modelcontextprotocol.io su lista de servidores MCP. Solo envía lo que buscas, nada sobre ti ni sobre tus servidores.';

  @override
  String get mcpCatalogConsentLoad => 'Cargar la lista';

  @override
  String get mcpCatalogForget => 'Dejar de usar el registro';

  @override
  String get mcpCatalogForgetFailed =>
      'No se pudo olvidar la lista del registro guardada. Reintenta.';

  @override
  String get mcpCatalogSearch => 'Buscar en el registro';

  @override
  String get mcpCatalogInventoryFailed =>
      'No se pudieron leer los servidores MCP de este servidor';

  @override
  String get mcpCatalogInventoryFailedBody =>
      'Los interruptores necesitan saber qué está ya activado. Revisa la conexión y reintenta.';

  @override
  String get mcpCatalogFailed => 'No se pudo cargar el registro público de MCP';

  @override
  String get mcpCatalogSearchFailed =>
      'No se pudo buscar en el registro público de MCP';

  @override
  String get mcpCatalogFailedBody =>
      'Revisa la conexión a internet del teléfono y reintenta. Aun así puedes escribir un servidor a mano.';

  @override
  String get mcpCatalogEmpty => 'El registro no listó ningún servidor';

  @override
  String mcpCatalogNoMatch(String query) {
    return 'Nada en el registro coincide con “$query”';
  }

  @override
  String get mcpCatalogEmptyBody =>
      'Prueba otras palabras, o escribe el servidor a mano.';

  @override
  String get mcpCatalogStale =>
      'No se pudo actualizar la lista desde el registro. Estas son las listas cargadas antes.';

  @override
  String get mcpCatalogPriceNote =>
      'El registro no lista precios. El dueño de un servidor alojado puede cobrar por él o pedir una cuenta.';

  @override
  String get mcpCatalogAdding => 'Agregando…';

  @override
  String get mcpCatalogRemoving => 'Quitando…';

  @override
  String get mcpCatalogCannotRemove =>
      'Activado. Este servidor lo mantiene en su configuración, y la app no puede quitarlo.';

  @override
  String get mcpCatalogNeedsDocker =>
      'Corre en Docker. Para agregarlo de todos modos, usa Escribir a mano.';

  @override
  String get mcpCatalogNoEndpoint =>
      'No lista nada que la app pueda arrancar. Para agregarlo de todos modos, usa Escribir a mano.';

  @override
  String mcpCatalogHostedBy(String host) {
    return 'Alojado por $host';
  }

  @override
  String get mcpCatalogNeedsNode => 'Necesita Node en el servidor';

  @override
  String get mcpCatalogNeedsNodePhone => 'Necesita Node en este teléfono';

  @override
  String get mcpCatalogNeedsPython => 'Necesita Python con uv en el servidor';

  @override
  String get mcpCatalogNeedsKey => 'Necesita una clave API';

  @override
  String get mcpCatalogNeedsSettings => 'Necesita ajustes extra';

  @override
  String mcpCatalogNodeTitle(String title) {
    return '$title corre con Node';
  }

  @override
  String get mcpCatalogNodeAdd => 'Agregar Node a este teléfono';

  @override
  String get mcpCatalogNodeAddDetail =>
      'Abre En este teléfono. Elige Agregar herramientas, luego Node, y activa esto otra vez cuando esté agregado.';

  @override
  String get mcpCatalogNodeHave => 'Node ya está en este teléfono';

  @override
  String get mcpCatalogNodeHaveDetail => 'Revisa los detalles y agrégalo';

  @override
  String mcpSetupFromCatalog(String listing, String server) {
    return 'Se llenó desde “$listing” del registro público de MCP. Revísalo antes de agregarlo: $server correrá o se conectará a lo que está aquí.';
  }

  @override
  String get mcpSetupThisServer => 'este servidor';

  @override
  String mcpSetupValueRequired(String name) {
    return 'Escribe un valor para $name';
  }

  @override
  String get mcpSetupNameFromCatalog => 'La lista del registro requiere este';

  @override
  String get mcpVariableName => 'Nombre de la variable';

  @override
  String get mcpVariableValue => 'Valor de la variable';

  @override
  String get mcpAddVariable => 'Agregar otra variable';

  @override
  String get mcpRemoveVariable => 'Quitar la variable';

  @override
  String get mcpSetupTimeoutSeconds => 'Límite de tiempo en segundos';

  @override
  String get isolatedTaskPromptLabel => '¿En qué debería trabajar?';

  @override
  String get isolatedTaskPromptHelper =>
      'Se envía cuando la copia esté lista. Déjalo vacío para escribirlo en la conversación.';

  @override
  String get isolatedTaskOptions => 'Opciones';

  @override
  String get isolatedTaskPreparingHint =>
      'Si dejas de esperar, la copia se queda. La encontrarás en Proyecto › Árboles de trabajo.';

  @override
  String get isolatedTaskFailedBody =>
      'La copia está hecha, pero su configuración no terminó. Empieza en ella de todos modos, o quítala.';

  @override
  String isolatedTaskSending(String name) {
    return 'Enviando tu tarea a $name…';
  }

  @override
  String get isolatedTaskSendFailed => 'No se pudo enviar tu tarea';

  @override
  String get isolatedTaskSendFailedBody =>
      'Está esperando en la caja de mensajes de la conversación, lista para enviar.';

  @override
  String get isolatedTaskSendFailedLost =>
      'Copia tu tarea abajo y envíala en la conversación.';

  @override
  String get isolatedTaskOpenConversation => 'Abrir la conversación';

  @override
  String get isolatedTaskStartAnyway => 'Empezar de todos modos';

  @override
  String get isolatedTaskRemove => 'Quitar la copia';

  @override
  String isolatedTaskRemoveTitle(String name) {
    return '¿Quitar $name?';
  }

  @override
  String get isolatedTaskRemoveBody =>
      'Su carpeta y su rama se eliminan. Tu proyecto mismo no se toca.';

  @override
  String isolatedTaskRemoved(String name) {
    return 'Se quitó $name. Puedes empezar de nuevo.';
  }

  @override
  String get isolatedTaskSetupOutput => 'Lo que la configuración reportó';

  @override
  String get isolatedTaskCopyFolder => 'Carpeta de la copia';

  @override
  String get isolatedTaskBranchLabel => 'Rama';

  @override
  String get isolatedTaskStageSend =>
      'Abriendo la conversación y enviando tu tarea';

  @override
  String get teamStartRunBlockedTitle => 'El equipo no puede tomar tareas';

  @override
  String get teamStartRunPlannerOff => 'El planificador está apagado';

  @override
  String get teamStartRunPlannerOffWakeBody =>
      'El planificador convierte cada tarea en pasos para el equipo. Despiértalo para darle tu tarea al equipo.';

  @override
  String get teamStartRunPlannerOffHostBody =>
      'El planificador convierte cada tarea en pasos para el equipo, y esta app no puede encenderlo. Enciéndelo donde corre el equipo y reintenta.';

  @override
  String get teamStartRunNoPlanner => 'Este equipo no tiene planificador';

  @override
  String get teamStartRunNoPlannerBody =>
      'Un planificador convierte cada tarea en pasos para el equipo. Agrega uno donde corre el equipo y reintenta.';

  @override
  String get teamStartRunNoProject => 'Este equipo aún no tiene proyecto';

  @override
  String get teamStartRunNoProjectBody =>
      'Las tareas van directas al ejecutor de un proyecto. Agrega un proyecto al equipo y reintenta.';

  @override
  String get teamStartRunWake => 'Despertar al planificador';

  @override
  String get teamStartRunWakeAsked =>
      'Despertando al planificador. El formulario de tarea se abre apenas despierte.';

  @override
  String get teamStartRunStillOff => 'El planificador sigue apagado.';

  @override
  String get teamStartRunStillNoProject => 'El equipo sigue sin proyecto.';

  @override
  String get teamStartRunWakeRefused => 'No se pudo despertar al planificador';

  @override
  String get teamStartRunWakeRefusedNext =>
      'Reintenta, o enciéndelo donde corre el equipo.';

  @override
  String get addServerTailscaleNext => 'Ingresa la dirección';

  @override
  String get setupProgressViewFailedStep =>
      'Este paso no terminó. Lo que falló está en Detalles.';

  @override
  String setupProgressViewFailedAt(String name) {
    return 'Se detuvo en $name. Lo que falló está en Detalles.';
  }

  @override
  String thisPhoneUpToDate(String version) {
    return 'Al día · $version';
  }

  @override
  String get thisPhoneStartAgain => 'Empezar otra vez';

  @override
  String thisPhoneInstallFailed(String runtime) {
    return 'Instalar $runtime no terminó. Instálalo otra vez; tus conversaciones se conservan.';
  }

  @override
  String get thisPhoneInstallAgain => 'Instalar otra vez';

  @override
  String thisPhoneStopFailed(String runtime) {
    return '$runtime no se detuvo. Intenta detenerlo otra vez.';
  }

  @override
  String thisPhoneCheckFailed(String runtime) {
    return 'Este teléfono no pudo revisar a $runtime. Reintenta en un momento.';
  }

  @override
  String get thisPhoneHistoryMoving =>
      'Llevando tus conversaciones anteriores…';

  @override
  String thisPhoneHistoryMovingCount(int done, int total) {
    return 'Llevando tus conversaciones anteriores ($done de $total)…';
  }

  @override
  String get thisPhoneHistoryFailed =>
      'No se pudieron traer tus conversaciones anteriores. Sus datos originales siguen en este teléfono, intactos.';

  @override
  String get addServerCheckFailedPlain =>
      'No se pudo comprobar el servidor. Revisa la dirección y la conexión de este teléfono e inténtalo de nuevo.';

  @override
  String serverRowDetailsTitle(String name) {
    return 'Detalles de $name';
  }

  @override
  String get teamUiHostGuideEnterAddress => 'Escribe la dirección';

  @override
  String get commandAuthStartFailed => 'El inicio de sesión no arrancó.';

  @override
  String get commandAuthCheckFailed =>
      'No se pudo revisar el inicio de sesión. Reintenta.';

  @override
  String get commandAuthTryAgain => 'Reintentar';

  @override
  String get draftLeaveMessageNoText =>
      'Intenta guardar de nuevo. Si sales sin guardar, tus últimos cambios en este borrador podrían perderse.';

  @override
  String get draftLeaveCopyAction => 'Copiar borrador y salir';

  @override
  String get draftLeaveRetry => 'Intentar guardar de nuevo';

  @override
  String get draftLeaveStillFailing =>
      'Sigue sin guardarse. Copia tu texto antes de salir.';

  @override
  String get queuedRetry => 'Intentar de nuevo';

  @override
  String queuedRetryAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Reintentar las $count',
    );
    return '$_temp0';
  }

  @override
  String chatUiUseModelAndResend(String model) {
    return 'Usar $model y reenviar';
  }

  @override
  String get chatUiChooseAnotherModel => 'Elegir otro modelo';

  @override
  String get chatUiSendPromptAgain => 'Enviar otra vez';

  @override
  String get chatUiPromptNotAnswered => 'Sin responder';

  @override
  String get chatWatchEndedTitle => 'Esta conversación terminó';

  @override
  String get chatWatchEndedBody =>
      'Terminó antes de que el ejecutor escribiera nada aquí.';

  @override
  String get chatWatchBackToTask => 'Volver a la tarea';

  @override
  String get chatWatchBackToWorker => 'Volver al ejecutor';

  @override
  String get integrationsSignInUncertainNext =>
      'Revisa el servidor antes de empezar de nuevo';

  @override
  String get inAppServerStartExitedBody =>
      'OpenCode se cerró solo al iniciarse. Abre la configuración para ver su registro, o inícialo de nuevo.';

  @override
  String inAppServerStartTimedOutBody(int seconds) {
    return 'OpenCode no respondió en $seconds segundos. Puede que el teléfono esté ocupado o con poca memoria; cierra otras apps e inícialo de nuevo.';
  }

  @override
  String get inAppServerStartInterruptedBody =>
      'El inicio se detuvo porque la app salió de la pantalla. Inícialo de nuevo para continuar.';

  @override
  String get inAppServerStartPasswordBody =>
      'La app no pudo configurar el inicio de sesión de OpenCode en este teléfono. Inícialo de nuevo; si se repite, abre la configuración.';

  @override
  String get inAppServerStartRefusedBody =>
      'El teléfono no permitió que la app iniciara OpenCode en este momento. Inícialo de nuevo; si se repite, reinicia el teléfono.';

  @override
  String get integrationsConnectWithKey => 'Agregar una clave API';

  @override
  String get integrationsConnectOnServer => 'Configurar en el servidor';

  @override
  String integrationsProviderDetails(String name) {
    return 'Detalles de $name';
  }

  @override
  String get integrationsEnvironmentVariable =>
      'Variable de entorno del servidor';

  @override
  String integrationsEnvironmentNote(String name) {
    return 'Para conectar $name sin la app, define esto donde corre el servidor y luego reinicia el servidor.';
  }

  @override
  String get aboutBundledComponents => 'Componentes incluidos';

  @override
  String get aboutBundledComponentsDetail =>
      'Íconos, fuentes y otras partes que vienen dentro de esta app';

  @override
  String get manageSpaceTitle => 'Vaciar el almacenamiento de esta app';

  @override
  String get manageSpaceMeasuring => 'Midiendo lo almacenado…';

  @override
  String get manageSpaceIntro =>
      'Vaciar elimina todo lo que OpenCode Mobile guarda en este teléfono, y no se puede deshacer. Exporta tus proyectos primero si quieres conservarlos.';

  @override
  String get manageSpaceExportFirst => 'Exportar los proyectos primero';

  @override
  String get manageSpaceClearCache => 'Vaciar solo la caché de la app';

  @override
  String manageSpaceClearCacheDetail(String size) {
    return 'Libera $size. Los proyectos, servidores y ajustes se quedan.';
  }

  @override
  String get manageSpaceClearCacheKeeps =>
      'Los proyectos, servidores y ajustes se quedan.';

  @override
  String manageSpaceCacheCleared(String size) {
    return 'Caché vaciada. $size liberados.';
  }

  @override
  String get manageSpaceCacheFailed => 'No se pudo vaciar la caché. Reintenta.';

  @override
  String get manageSpaceTryAgain => 'Reintentar';

  @override
  String get manageSpaceDeleteAll => 'Eliminar todo';

  @override
  String get manageSpaceDeleteAllDetail =>
      'Elimina toda la lista de abajo y cierra la app';

  @override
  String get manageSpaceDeleteTitle => '¿Eliminar todo?';

  @override
  String get manageSpaceDeleteBody =>
      'OpenCode Mobile entonces empieza otra vez como si fuera nuevo. Esto no se puede deshacer.';

  @override
  String get manageSpaceLostServer => 'En este teléfono y sus conversaciones';

  @override
  String manageSpaceLostProjects(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count proyectos ($size)',
      one: '1 proyecto ($size)',
    );
    return '$_temp0';
  }

  @override
  String manageSpaceLostSettings(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count servidores guardados y todos los ajustes',
      one: '1 servidor guardado y todos los ajustes',
      zero: 'Todos los ajustes',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptAll =>
      'Tus computadoras y lo que hayas empujado a git se quedan';

  @override
  String get manageSpaceWaitForExport => 'Espera a que termine la exportación';

  @override
  String get manageSpaceDeletedLabel => 'Vaciar elimina';

  @override
  String get manageSpaceServer => 'En este teléfono';

  @override
  String get manageSpaceServerDetail =>
      'Ubuntu, OpenCode, sus inicios de sesión y sus conversaciones';

  @override
  String get manageSpaceSettings => 'Servidores guardados y ajustes';

  @override
  String manageSpaceSavedServers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count servidores guardados',
      one: '1 servidor guardado',
      zero: 'Sin servidores guardados',
    );
    return '$_temp0';
  }

  @override
  String get manageSpaceKeptLabel => 'Se queda';

  @override
  String get manageSpaceKeptComputers => 'Tus computadoras y sus servidores';

  @override
  String get manageSpaceKeptGit => 'Lo que hayas empujado a git';

  @override
  String projectExportDetail(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count proyectos, $size, como un archivo zip que guardas donde quieras',
      one: '1 proyecto, $size, como un archivo zip que guardas donde quieras',
    );
    return '$_temp0';
  }

  @override
  String get projectExportNoProjects =>
      'Aún no hay proyectos en En este teléfono';

  @override
  String get projectExportSave => 'Guardar como archivo zip';

  @override
  String get projectExportRunning => 'Exportando los proyectos';

  @override
  String get projectExportPreparing => 'Listando archivos…';

  @override
  String projectExportProgress(String done, String total) {
    return '$done de $total';
  }

  @override
  String get projectExportStop => 'Detener la exportación';

  @override
  String get projectExportStopDetail =>
      'El archivo a medio escribir se elimina';

  @override
  String get projectExportPrivate =>
      'Incluir inicios de sesión y conversaciones';

  @override
  String get projectExportPrivateDetail =>
      'Privado: cualquiera con el archivo puede usar tus cuentas';

  @override
  String projectExportDone(String size, int files) {
    return 'Proyectos exportados: $size en $files archivos.';
  }

  @override
  String get projectExportDonePrivate =>
      'Este archivo contiene inicios de sesión. Mantenlo privado.';

  @override
  String projectExportDoneLeftOut(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count archivos con inicios de sesión o claves se quedaron fuera.',
      one: '1 archivo con inicios de sesión o claves se quedó fuera.',
    );
    return '$_temp0';
  }

  @override
  String get projectExportStopped => 'Exportación detenida. No se guardó nada.';

  @override
  String get projectExportFailedDestination =>
      'No se pudo escribir en el lugar que elegiste. Reintenta, o elige otro lugar.';

  @override
  String get projectExportFailedSpace =>
      'El lugar que elegiste está lleno. Libera espacio allá o elige otro lugar.';

  @override
  String get projectExportFailedSource =>
      'No se pudo leer un archivo de proyecto. Reintenta.';

  @override
  String get projectExportFailed =>
      'La exportación se detuvo antes de terminar. Reintenta.';

  @override
  String get projectExportProjectsLabel => 'Proyectos';

  @override
  String get thisPhoneExportProjects => 'Exportar los proyectos';

  @override
  String get thisPhoneExportProjectsDetail =>
      'Guárdalos como un archivo zip, para conservarlos o moverlos';

  @override
  String get demoNoCommands =>
      'La demo no tiene comandos: envía el mensaje de ejemplo para ver una revisión de cambios.';

  @override
  String e7ModelUiUnusableProviders(int count, String providers) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Sesión iniciada en $providers, pero este servidor no pudo cargar esos inicios de sesión ni tras recargar, así que sus modelos no pueden responder. Los inicios de sesión por navegador de algunos proveedores, como Anthropic y Google, no se cargan en este servidor. Agrega una clave de API en Proveedores o elige otro modelo.',
      one:
          'Sesión iniciada en $providers, pero este servidor no pudo cargar ese inicio de sesión ni tras recargar, así que sus modelos no pueden responder. Los inicios de sesión por navegador de algunos proveedores, como Anthropic y Google, no se cargan en este servidor. Agrega una clave de API en Proveedores o elige otro modelo.',
    );
    return '$_temp0';
  }

  @override
  String e7ModelUiProviderReloadWaits(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'La recarga espera a que terminen $count respuestas en curso, porque recargar las detendría.',
      one:
          'La recarga espera a que termine 1 respuesta en curso, porque recargar la detendría.',
    );
    return '$_temp0';
  }

  @override
  String get freeModelNotice =>
      'Usas el modelo gratuito de OpenCode, que es más lento. Agrega una clave API de tu proveedor para usar el tuyo.';

  @override
  String get chatFreeModelNote => 'Modelo gratuito, más lento';

  @override
  String get chatFreeModelAddKey => 'Agregar tu clave';

  @override
  String get freeModelSignIn => 'Agregar una clave API';

  @override
  String get replySpeedTitle => 'Velocidad de respuesta';

  @override
  String replySpeedLast(String first, String total) {
    return 'Última respuesta: primeras palabras después de $first, terminada después de $total';
  }

  @override
  String replySpeedNoWords(String total) {
    return 'Última respuesta: terminó después de $total antes de que salieran palabras';
  }

  @override
  String replySpeedSeconds(String seconds) {
    return '$seconds s';
  }

  @override
  String get perfDetailLinuxMode => 'Modo de velocidad de Linux';

  @override
  String get perfLinuxModeFast => 'Rápido: proot con seccomp';

  @override
  String get perfLinuxModeSlow => 'Lento: proot sin seccomp';

  @override
  String get perfLinuxModeUnknown =>
      'No se sabe mientras OpenCode está detenido';

  @override
  String get perfDetailAwake => 'Teléfono mantenido despierto';

  @override
  String get perfAwakeNow => 'Ahora, mientras corre una respuesta';

  @override
  String get perfAwakeWhenWorking => 'Solo mientras corre una respuesta';

  @override
  String get perfDetailFirstWords => 'Primeras palabras, última respuesta';

  @override
  String perfFirstWordsSplit(String app, String server) {
    return '$app en la app · $server en el servidor';
  }

  @override
  String get perfDetailModel => 'Modelo, última respuesta';

  @override
  String get manageSpaceIntroNothingToExport =>
      'Vaciar elimina todo lo que OpenCode Mobile guarda en este teléfono, y no se puede deshacer.';

  @override
  String integrationsKeyOnlyHelper(String name) {
    return '$name no permite inicio de sesión por navegador desde otras apps, así que usa una clave API. Se factura aparte de cualquier suscripción. La clave se guarda en este servidor y nunca se muestra de nuevo.';
  }

  @override
  String integrationsGetKey(String name) {
    return 'Conseguir una clave de $name';
  }

  @override
  String integrationsKeySavedReady(String name) {
    return 'Clave de $name guardada. Elige uno de sus modelos en el selector de modelos.';
  }

  @override
  String integrationsKeySavedWaiting(String name) {
    return 'Clave de $name guardada. Carga cuando terminen las respuestas en marcha.';
  }

  @override
  String integrationsKeySavedUnusable(String name) {
    return 'Clave de $name guardada, pero este servidor no pudo cargarla después de una actualización. Revisa la clave, o prueba Recargar proveedores en el selector de modelos.';
  }

  @override
  String integrationsKeySavedPending(String name) {
    return 'Clave de $name guardada. El servidor aún no la ha cargado.';
  }

  @override
  String get pickerConnectProvider => 'Conectar un proveedor';

  @override
  String get pickerConnectProviderHint =>
      'Agrega una clave API o inicia sesión para usar sus modelos';

  @override
  String pickerAddKeyFor(String name) {
    return 'Agregar una clave API para $name';
  }

  @override
  String get pickerAddKeyNotConnectedHint =>
      'Sus modelos aún no están en esta lista';

  @override
  String pickerSignInTo(String name) {
    return 'Iniciar sesión en $name';
  }

  @override
  String get pickerSignInHint =>
      'Abre las opciones de inicio de sesión de este servidor';

  @override
  String get pickerFreeOnlyNote =>
      'Solo está disponible el modelo gratuito de OpenCode, que es más lento.';

  @override
  String pickerProviderReady(String name) {
    return '$name está listo. Sus modelos están en la lista.';
  }

  @override
  String pickerProviderNotLoaded(String name) {
    return '$name está guardado, pero el servidor aún no lo ha cargado.';
  }

  @override
  String get integrationsSignedInUnusable =>
      'Sesión iniciada, pero este servidor no puede usarla';

  @override
  String get chatUiCompactConfirmTitle => '¿Compactar esta conversación?';

  @override
  String get chatUiCompactConfirmBody =>
      'Compactar reemplaza los mensajes anteriores con un resumen corto para ahorrar espacio. No se puede deshacer.';

  @override
  String get chatUiCompactConfirmAction => 'Compactar conversación';

  @override
  String get kitTurnReconnecting =>
      'Se perdió la conexión. Reconectando para recibir el resto de esta respuesta.';

  @override
  String get kitTurnLiveSending => 'Enviando';

  @override
  String get kitTurnLiveWaitingForServer => 'Esperando al servidor';

  @override
  String get kitTurnLiveServerQuiet => 'El servidor aún no ha respondido';

  @override
  String get kitTurnLiveThinking => 'Pensando';

  @override
  String get kitTurnLiveFirstWordSlow =>
      'Esperando la primera palabra del modelo';

  @override
  String get kitTurnLiveFirstWordSlowTeam =>
      'AI Team también está trabajando en este teléfono, así que las respuestas pueden tardar más';

  @override
  String get kitTurnLiveWriting => 'Escribiendo';

  @override
  String get kitTurnLiveWorking => 'Trabajando';

  @override
  String get kitTurnLiveWaitingForYou => 'Esperándote';

  @override
  String get kitTurnLiveStop => 'Detener respuesta';

  @override
  String kitTurnLiveNow(String status) {
    return '$status…';
  }

  @override
  String kitTurnLiveFor(String status, String elapsed) {
    return '$status · $elapsed';
  }

  @override
  String kitTurnLiveSeconds(int seconds) {
    return '$seconds s';
  }

  @override
  String kitTurnLiveMinutes(int minutes, int seconds) {
    return '$minutes min $seconds s';
  }

  @override
  String get chatNoReplyCameBack => 'No volvió ninguna respuesta';

  @override
  String get composerFieldLabel => 'Mensaje para el agente';

  @override
  String get e7WorkspaceYesterday => 'Ayer';

  @override
  String teamProgressOne(String title) {
    return 'AI Team: $title';
  }

  @override
  String teamProgressStep(String title, int done, int total) {
    return 'AI Team: $title · paso $done de $total';
  }

  @override
  String teamProgressMany(int count) {
    return 'AI Team: $count tareas en marcha';
  }

  @override
  String get teamSettingsTitle => 'Ajustes del equipo';

  @override
  String get teamSettingsOpenTooltip => 'Ajustes del equipo';

  @override
  String get teamSettingsTurnOff => 'Apagar el AI Team';

  @override
  String teamChatWorkerNumbered(String role, int n) {
    return '$role $n';
  }

  @override
  String get teamRoleNameGeneral => 'General';

  @override
  String get teamRoleNameProduct => 'Producto';

  @override
  String get teamRoleNameFrontend => 'Frontend';

  @override
  String get teamRoleNameBackend => 'Backend';

  @override
  String get teamRoleNameTester => 'Probador';

  @override
  String get teamRolePurposeGeneral =>
      'Cualquier tarea, hecha de la manera simple';

  @override
  String get teamRolePurposeProduct =>
      'Convierte una idea en requisitos claros y un plan';

  @override
  String get teamRolePurposeFrontend =>
      'Pantallas, disposición y cómo se siente usarlo';

  @override
  String get teamRolePurposeBackend =>
      'Servidores, datos y el código detrás de las pantallas';

  @override
  String get teamRolePurposeTester =>
      'Encuentra lo que se rompe y muestra que funciona';

  @override
  String get teamRolesTitle => 'Agentes';

  @override
  String get teamRolesNew => 'Rol nuevo';

  @override
  String teamRoleWorkingOn(String task, String age) {
    return 'Trabajando en “$task” · hace $age';
  }

  @override
  String teamRoleUses(String model) {
    return 'Usa $model';
  }

  @override
  String get teamRoleUsesTeamModel => 'Usa el modelo del equipo';

  @override
  String get teamRoleUsesComputerModel => 'Usa el modelo de la computadora';

  @override
  String teamRoleTaskCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tareas',
      one: '1 tarea',
      zero: 'Aún no hay tareas',
    );
    return '$_temp0';
  }

  @override
  String teamSettingsAgentsRow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Agentes · $count roles',
      one: 'Agentes · 1 rol',
    );
    return '$_temp0';
  }

  @override
  String get teamSettingsAgentsHint =>
      'Quién hace el trabajo y cómo trabaja cada uno';

  @override
  String get teamRoleNewTitle => 'Rol nuevo';

  @override
  String get teamRoleFieldName => 'Nombre';

  @override
  String get teamRoleFieldPurpose => 'Para qué sirve';

  @override
  String get teamRoleFieldPurposeHint =>
      'Una línea, por ejemplo: escribe las guías';

  @override
  String get teamRoleFieldInstructions => 'Instrucciones';

  @override
  String get teamRoleFieldInstructionsHint =>
      'Cómo debe trabajar este rol, en tus propias palabras';

  @override
  String get teamRoleNameRequired => 'Dale un nombre al rol';

  @override
  String get teamRoleModelRow => 'Modelo';

  @override
  String get teamRoleTeamModel => 'Modelo del equipo';

  @override
  String get teamRoleComputerModel => 'El modelo de la computadora';

  @override
  String get teamRoleModelSheetDefault => 'Modelo del equipo';

  @override
  String get teamRoleModelSheetDefaultHint =>
      'Usa el modelo que use todo el equipo';

  @override
  String teamRoleWorkingNow(String task) {
    return 'Trabajando en “$task”';
  }

  @override
  String get teamRoleOpenConversation => 'Abrir su conversación';

  @override
  String get teamRoleRecentTasks => 'Tareas recientes';

  @override
  String teamRoleNoTasks(String role) {
    return 'Aún nada se le ha dado a $role';
  }

  @override
  String teamRoleGiveTask(String role) {
    return 'Darle una tarea a $role';
  }

  @override
  String get teamRoleSave => 'Guardar';

  @override
  String get teamRoleCreate => 'Crear el rol';

  @override
  String teamRoleReset(String role) {
    return 'Reajustar $role';
  }

  @override
  String teamRoleResetTitle(String role) {
    return '¿Reajustar $role?';
  }

  @override
  String get teamRoleResetBody =>
      'Su nombre, propósito, instrucciones y modelo vuelven a como vinieron de fábrica.';

  @override
  String teamRoleDelete(String role) {
    return 'Eliminar $role';
  }

  @override
  String teamRoleDeleteTitle(String role) {
    return '¿Eliminar $role?';
  }

  @override
  String teamRoleDeleteBody(String role) {
    return '$role sale de este equipo. Las tareas que ya hizo conservan su nombre.';
  }

  @override
  String get teamRoleWorkerName => 'Nombre del ejecutor';

  @override
  String get teamRoleExamplesLabel => 'Empezar desde un ejemplo';

  @override
  String get teamRoleExampleDocs => 'Escritor de docs';

  @override
  String get teamRoleExampleDocsPurpose => 'Escribe y actualiza las guías';

  @override
  String get teamRoleExampleDocsInstructions =>
      'Escribes y actualizas la documentación. Mantenla corta, exacta y en palabras simples. Verifica cada comando y ruta que menciones antes de anotarlo.';

  @override
  String get teamRoleExampleSecurity => 'Revisor de seguridad';

  @override
  String get teamRoleExampleSecurityPurpose =>
      'Busca formas en que el código podría abusarse';

  @override
  String get teamRoleExampleSecurityInstructions =>
      'Revisas el código por problemas de seguridad: secretos en código o logs, entrada sin revisar, enlaces inseguros y faltantes de verificación de permisos. Reporta lo que encuentres con el archivo y la línea, y arregla solo lo que la tarea pide.';

  @override
  String get teamRoleExampleDesigner => 'Diseñador';

  @override
  String get teamRoleExampleDesignerPurpose =>
      'Lo vuelve claro, consistente y agradable';

  @override
  String get teamRoleExampleDesignerInstructions =>
      'Mejoras el aspecto y la lectura del producto. Reutiliza las partes y palabras que ya están en la app, mantiene un solo lenguaje de diseño y revisa pantallas pequeñas y texto grande.';

  @override
  String get teamRoleStarterInstructions =>
      'Eres el/la ___ de este equipo.\nEnfócate en: ___\nSiempre: ___\nNunca: ___';

  @override
  String get teamStartRunWho => 'Quién';

  @override
  String get teamStartRunWhoSuggested => 'Sugerido a partir de tus palabras';

  @override
  String get teamStartRunWhoChange => 'Cambiar';

  @override
  String get teamStartRunWhoTitle => '¿Quién debería tomar esto?';

  @override
  String teamChatLeadStartingRole(String role, String title) {
    return '$role empezó en “$title”';
  }

  @override
  String teamChatLeadClaimedRole(String role, String title) {
    return '$role tomó “$title”';
  }

  @override
  String teamChatLeadStartingItRole(String role) {
    return '$role arrancó';
  }

  @override
  String teamChatLeadClaimedItRole(String role) {
    return '$role tomó la tarea';
  }

  @override
  String get teamRolesSearchAliases =>
      'roles personas agentes equipo frontend backend tester producto diseñador instrucciones';

  @override
  String get teamUiStatePhoneStoppedTitle => 'AI Team detenido';

  @override
  String get teamUiStatePhoneStoppedBody =>
      'AI Team en este teléfono no corre. Inícialo para continuar tus tareas.';

  @override
  String get teamStartStepService => 'Iniciando el servicio del equipo';

  @override
  String get teamStartStepAnswering => 'Esperando que el equipo responda';

  @override
  String get teamStartStepStore => 'Abriendo el almacén de tareas';

  @override
  String get teamStartStepAgents => 'Preparando a los agentes';

  @override
  String get teamStartSlow =>
      'Tardando más de lo normal, el teléfono está ocupado';

  @override
  String get teamStartAgain => 'Empezar otra vez';

  @override
  String get teamUiHostPhraseStarting => 'Iniciando';

  @override
  String get teamUiStartOnPhone => 'Iniciar AI Team en este teléfono';

  @override
  String get chatCollapseAllSteps => 'Contraer todos los pasos';

  @override
  String chatWatchTeamInstructions(int count, String time) {
    final intl.NumberFormat countNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String countString = countNumberFormat.format(count);

    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Instrucciones del equipo · $countString palabras · $time',
      one: 'Instrucciones del equipo · 1 palabra · $time',
    );
    return '$_temp0';
  }

  @override
  String get chatWatchEmptyStartingTitle => 'Arrancando';

  @override
  String get chatWatchEmptyStartingBody =>
      'Sus pasos aparecen aquí mientras trabaja.';

  @override
  String chatWatchEmptyWorkingOn(String task) {
    return 'Trabajando en “$task”. Sus pasos aparecen aquí mientras trabaja.';
  }

  @override
  String chatWatchEmptyReviewing(String task) {
    return 'Revisando los cambios de “$task”. Sus pasos aparecen aquí mientras trabaja.';
  }

  @override
  String get chatWatchEmptyIdleTitle => 'Esperando';

  @override
  String get chatWatchEmptyIdleBody =>
      'Está esperando su próxima tarea. Escríbele abajo para pedir algo.';

  @override
  String get teamUiAgentLabelSessionTitle => 'Título de la conversación';

  @override
  String get kitComposerPillNoAnswer => 'Aún sin respuesta';

  @override
  String get kitComposerRailRetry => 'Intentar de nuevo';

  @override
  String get teamProjectHome => 'AI Team';

  @override
  String get teamProjectDemo => 'Demo';

  @override
  String get teamProjectNew => 'Proyecto nuevo';

  @override
  String get teamProjectQuick => 'Dar una tarea rápida';

  @override
  String get teamProjectSettings => 'Ajustes del proyecto';

  @override
  String get teamProjectRoles => 'Roles y agentes';

  @override
  String get teamProjectEmpty =>
      'Dale una meta a tu equipo para empezar un proyecto.';

  @override
  String get teamProjectSelect => 'Elige un proyecto';

  @override
  String get teamProjectSelectTask =>
      'Elige una tarea para seguir su conversación.';

  @override
  String get teamProjectLoad => 'Cargando proyectos';

  @override
  String get teamProjectRetry => 'Reintentar';

  @override
  String get teamProjectError =>
      'No se pudo actualizar el proyecto. Tu trabajo guardado sigue disponible.';

  @override
  String get teamProjectSpec => 'Especificación abierta';

  @override
  String get teamProjectPlan => 'Revisar el plan';

  @override
  String get teamProjectBoard => 'Tablero';

  @override
  String get teamProjectGraph => 'Dependencias';

  @override
  String get teamProjectTimeline => 'Cronología';

  @override
  String get teamProjectServers => 'Servidores';

  @override
  String get teamProjectMilestones => 'Hitos';

  @override
  String get teamProjectMerge => 'Fila de fusión';

  @override
  String get teamProjectCost => 'Costo';

  @override
  String get teamProjectDecisions => 'Decisiones recientes';

  @override
  String get teamProjectPause => 'Pausar el proyecto';

  @override
  String get teamProjectResume => 'Reanudar el proyecto';

  @override
  String get teamProjectStopConfirmTitle => '¿Detener este proyecto?';

  @override
  String get teamProjectStop => 'Detener el proyecto';

  @override
  String get teamProjectStopBody =>
      'Las tareas en marcha se detendrán. Su trabajo y el historial del proyecto se conservarán.';

  @override
  String get teamProjectAdvance => 'Avanzar el demo';

  @override
  String get teamProjectDigest => 'Desde que no estabas';

  @override
  String get teamProjectDigestRead => 'Marcar como leído';

  @override
  String get teamProjectAnswer => 'Responder';

  @override
  String get teamProjectAnswerLabel => 'Tu respuesta';

  @override
  String get teamProjectAll => 'Todo';

  @override
  String get teamProjectMerges => 'Fusiones';

  @override
  String get teamProjectProblems => 'Problemas';

  @override
  String get teamProjectMilestoneFilter => 'Hito';

  @override
  String get teamProjectRepoFilter => 'Repositorio';

  @override
  String get teamProjectServerFilter => 'Servidor';

  @override
  String get teamProjectBacklog => 'Backlog';

  @override
  String get teamProjectReady => 'Listas';

  @override
  String get teamProjectWorking => 'En marcha';

  @override
  String get teamProjectReview => 'Revisión';

  @override
  String get teamProjectDone => 'Terminadas';

  @override
  String get teamProjectNoTasks => 'No hay tareas en esta vista.';

  @override
  String get teamProjectMove => 'Mover la tarea';

  @override
  String get teamProjectMoveTo => 'Mover al servidor';

  @override
  String get teamProjectHandoff => 'Nota de relevo';

  @override
  String get teamProjectPaused => 'Pausado';

  @override
  String get teamProjectStopped => 'Detenido';

  @override
  String get teamProjectFailed => 'Se detuvo inesperadamente';

  @override
  String get teamProjectStalled => 'Sin progreso reciente';

  @override
  String get teamProjectPlanning => 'Dando forma a la especificación';

  @override
  String get teamProjectPlanWaiting => 'Plan listo para revisar';

  @override
  String get teamProjectNeedsYou => 'Necesita tu decisión';

  @override
  String get teamProjectWaiting => 'Esperando dependencias';

  @override
  String get teamProjectOnline => 'Alcanzable';

  @override
  String get teamProjectOffline => 'No alcanzable · últimas tareas conocidas';

  @override
  String get teamProjectNoLimit => 'Sin límite';

  @override
  String get teamProjectUnknown => 'No reportado';

  @override
  String get teamProjectAcceptMilestoneConfirmTitle => '¿Aceptar este hito?';

  @override
  String get teamProjectAccept => 'Aceptar el hito';

  @override
  String get teamProjectMergeConfirmTitle => '¿Fusionar a dev?';

  @override
  String get teamProjectMergeNext => 'Fusionar el trabajo revisado a dev';

  @override
  String get teamProjectCostDemo =>
      'Las cifras del demo son simuladas; memoria del dispositivo, batería, calor y velocidad de conversación no se han medido.';

  @override
  String teamProjectPlanningElapsed(int minutes) {
    return 'Dando forma a la especificación · $minutes min';
  }

  @override
  String teamProjectPlanningSlow(int minutes) {
    return 'Siguiendo con la especificación · $minutes min';
  }

  @override
  String teamProjectAllTasksDone(int total) {
    return 'Las $total tareas completas';
  }

  @override
  String teamProjectProgress(int done, int total, int working) {
    String _temp0 = intl.Intl.pluralLogic(
      working,
      locale: localeName,
      other: ' · $working en marcha',
      zero: '',
    );
    return '$done de $total tareas completas$_temp0';
  }

  @override
  String teamProjectLaneCount(int busy, int total) {
    return '$busy de $total carriles ocupados';
  }

  @override
  String get teamProjectEditorNewProject => 'Proyecto nuevo';

  @override
  String get teamProjectEditorQuickTask => 'Tarea rápida';

  @override
  String get teamProjectEditorSpec => 'Especificación viva';

  @override
  String get teamProjectEditorPlan => 'Revisar el plan';

  @override
  String get teamProjectEditorSettings => 'Ajustes del proyecto';

  @override
  String get teamProjectEditorRoles => 'Roles y agentes';

  @override
  String get teamProjectEditorStartPlanning => 'Empezar a planificar';

  @override
  String get teamProjectEditorStartTask => 'Empezar la tarea';

  @override
  String get teamProjectEditorApproveSpec => 'Aprobar la especificación';

  @override
  String get teamProjectEditorApprovePlan => 'Aprobar y empezar';

  @override
  String get teamProjectEditorSave => 'Guardar los cambios';

  @override
  String get teamProjectEditorSaveDraft => 'Guardar el borrador';

  @override
  String get teamProjectEditorName => 'Nombre del proyecto';

  @override
  String get teamProjectEditorGoal => 'Meta';

  @override
  String get teamProjectEditorRepos => 'Repositorios';

  @override
  String get teamProjectEditorRepoName => 'Nombre del repositorio';

  @override
  String get teamProjectEditorRepoPath => 'Carpeta del repositorio';

  @override
  String get teamProjectEditorServer => 'Servidor';

  @override
  String get teamProjectEditorRemove => 'Quitar';

  @override
  String get teamProjectEditorAddRepo => 'Agregar un repo';

  @override
  String get teamProjectEditorRole => 'Rol';

  @override
  String get teamProjectEditorPlanFirst => 'Primero planificar';

  @override
  String get teamProjectEditorMode => 'Modo de ejecución';

  @override
  String get teamProjectEditorSingle => 'Un solo carril';

  @override
  String get teamProjectEditorParallel => 'Agentes en paralelo';

  @override
  String get teamProjectEditorMaxLanes => 'Carriles máximos';

  @override
  String teamProjectEditorCostMeasured(String host, String memory) {
    return 'En $host: cerca de $memory MB de memoria por carril, medido. Batería y velocidad de conversación aún no medidas.';
  }

  @override
  String teamProjectEditorCostNotMeasured(String host) {
    return 'Aún no medido en $host. Tu conversación va primero.';
  }

  @override
  String get teamProjectEditorCostNoHost =>
      'Elige dónde corre el trabajo para ver lo que cuesta un carril allí.';

  @override
  String get teamProjectEditorThisPhone => 'este teléfono';

  @override
  String get teamProjectEditorGoalRequired => 'Agrega una meta.';

  @override
  String get teamProjectEditorRepoMissing => 'Agrega al menos un repositorio.';

  @override
  String get teamProjectEditorRepoIncomplete =>
      'Termina el repo: un nombre, una carpeta y dónde corre.';

  @override
  String get teamProjectEditorNoFallback => 'Sin modelo de respaldo';

  @override
  String get teamProjectEditorNoFallbackHint =>
      'El trabajo espera al modelo principal en vez de cambiarse.';

  @override
  String get teamProjectEditorReadOnlyRole =>
      'Solo lectura: este agente puede leer el proyecto pero no cambiarlo.';

  @override
  String get teamProjectEditorReadOnlyShort => 'Solo lectura';

  @override
  String get teamProjectEditorCharging => 'Solo cargando';

  @override
  String get teamProjectEditorReview => 'Nivel de revisión';

  @override
  String get teamProjectEditorMilestonesRisk => 'Hitos y puntos de riesgo';

  @override
  String get teamProjectEditorEveryStep => 'Cada paso';

  @override
  String get teamProjectEditorBudget => 'Presupuesto';

  @override
  String get teamProjectEditorSetLimits => 'Poner límites';

  @override
  String get teamProjectEditorNoLimit => 'Sin límite';

  @override
  String get teamProjectEditorDailyBudget => 'Por día (USD)';

  @override
  String get teamProjectEditorTotalBudget => 'Total (USD)';

  @override
  String get teamProjectEditorTaskTokens =>
      'Límite de tokens por tarea (opcional)';

  @override
  String get teamProjectEditorAutoFix =>
      'Arreglar los hallazgos automáticamente';

  @override
  String get teamProjectEditorMaxRounds => 'Rondas máximas de arreglo';

  @override
  String get teamProjectEditorConstraints => 'Restricciones';

  @override
  String get teamProjectEditorDecisions => 'Decisiones';

  @override
  String get teamProjectEditorOutOfScope => 'Fuera de alcance';

  @override
  String get teamProjectEditorMilestones => 'Hitos';

  @override
  String get teamProjectEditorMilestoneTitle => 'Título del hito';

  @override
  String get teamProjectEditorCriteria =>
      'Criterios de aceptación (uno por línea)';

  @override
  String get teamProjectEditorMoveUp => 'Subir';

  @override
  String get teamProjectEditorMoveDown => 'Bajar';

  @override
  String get teamProjectEditorAddMilestone => 'Agregar un hito';

  @override
  String get teamProjectEditorHistory => 'Historial de versiones';

  @override
  String get teamProjectEditorVersion => 'Versión';

  @override
  String get teamProjectEditorPlanHelp =>
      'Revisa las tareas y sus criterios de aceptación. Los cambios aquí se incluyen cuando apruebes el plan.';

  @override
  String get teamProjectEditorRisky => 'Compuerta de revisión · riesgoso';

  @override
  String get teamProjectEditorTaskTitle => 'Título de la tarea';

  @override
  String get teamProjectEditorRepo => 'Repo';

  @override
  String get teamProjectEditorDependencies => 'Depende de';

  @override
  String get teamProjectEditorRemoveTask => 'Quitar la tarea';

  @override
  String get teamProjectEditorAddRole => 'Agregar un rol';

  @override
  String get teamProjectEditorRoleName => 'Nombre del rol';

  @override
  String get teamProjectEditorInstructions => 'Instrucciones';

  @override
  String get teamProjectEditorModel => 'Modelo';

  @override
  String get teamProjectEditorFallback => 'Modelo de respaldo';

  @override
  String get teamProjectEditorAllRoles => 'Todos los roles';

  @override
  String get teamProjectEditorChooseMode =>
      'Elige Un solo carril o Agentes en paralelo.';

  @override
  String get teamProjectEditorPositiveLanes =>
      'Escribe un límite de carriles de 1 a 32.';

  @override
  String get teamProjectEditorChooseBudget =>
      'Pon un presupuesto o elige Sin límite.';

  @override
  String get teamProjectEditorPositiveBudget =>
      'Escribe un límite por día y un límite total, cada uno mayor que cero.';

  @override
  String get teamProjectEditorSaveFailed =>
      'No se pudieron guardar los cambios. Tus ediciones siguen aquí; reintenta guardar.';

  @override
  String get teamProjectEditorRequired =>
      'Agrega una meta y al menos un repositorio.';

  @override
  String get teamProjectEditorChooseRoleServer =>
      'Elige un rol y un servidor para esta tarea.';

  @override
  String get teamProjectEditorRepoRequired =>
      'Elige un servidor y escribe el nombre y la carpeta del repo.';

  @override
  String get teamProjectEditorSpecRequired =>
      'Agrega una meta y al menos un hito con título y criterios de aceptación.';

  @override
  String get teamProjectEditorDraftFailed =>
      'El borrador no se pudo conservar en este dispositivo. Mantén esta pantalla abierta y reintenta guardar.';

  @override
  String get teamProjectEditorChangedElsewhere =>
      'Este proyecto cambió mientras lo editabas. Cierra esta hoja y revisa el proyecto más reciente antes de aprobar cambios.';

  @override
  String get teamProjectTaskMissing => 'Esta tarea ya no está disponible';

  @override
  String get teamProjectRefreshTask => 'Actualizar la tarea';

  @override
  String get teamProjectTaskSaveFailed =>
      'El cambio no se guardó. Actualiza y reintenta; tu mensaje sigue aquí.';

  @override
  String get teamProjectTaskMessage => 'Escribirle al equipo…';

  @override
  String get teamProjectTaskInstructions => 'Instrucciones del equipo';

  @override
  String get teamProjectTaskApprovePlan => 'Aprobar y empezar';

  @override
  String get teamProjectTaskReview => 'Revisión requerida';

  @override
  String get teamProjectTaskAccepted => 'Aceptada';

  @override
  String get teamProjectTaskAcceptPhase => 'Aceptar la fase';

  @override
  String get teamProjectTaskFindings => 'Hallazgos de verificación';

  @override
  String get teamProjectTaskFix => 'Arreglar lo seleccionado';

  @override
  String get teamProjectTaskRecheck => 'Re-revisar la tarea';

  @override
  String get teamProjectTaskIgnore => 'Ignorar el hallazgo seleccionado';

  @override
  String get teamProjectTaskIgnoreReason =>
      '¿Por qué es seguro ignorar este hallazgo?';

  @override
  String get teamProjectTaskReasonRequired =>
      'Escribe una razón para conservarla con esta decisión.';

  @override
  String get teamProjectTaskCritical => 'Crítico';

  @override
  String get teamProjectTaskMajor => 'Mayor';

  @override
  String get teamProjectTaskMinor => 'Menor';

  @override
  String get teamProjectTaskMerge => 'Fila de fusión a dev';

  @override
  String get teamProjectTaskMergeRun => 'Revisar y fusionar a dev';

  @override
  String get teamProjectTaskPromoteConfirmTitle => '¿Promover dev a main?';

  @override
  String get teamProjectTaskPromote => 'Promover dev a main';

  @override
  String get teamProjectTaskPromoteBody =>
      'Esto actualiza main protegido al commit de dev que revisaste. El motor revisará ambos commits otra vez antes de cambiar main.';

  @override
  String get teamProjectTaskDiff => 'Ver los cambios';

  @override
  String get teamProjectTaskPause => 'Pausar la tarea';

  @override
  String get teamProjectTaskResume => 'Reanudar la tarea';

  @override
  String get teamProjectTaskStopConfirmTitle => '¿Detener esta tarea?';

  @override
  String get teamProjectTaskStop => 'Detener la tarea';

  @override
  String get teamProjectTaskStopBody =>
      'Detiene esta tarea y conserva su conversación y sus cambios para revisar.';

  @override
  String get teamProjectTaskRestart => 'Empezar la tarea otra vez';

  @override
  String get teamProjectTaskAnswer => 'Enviar la respuesta';

  @override
  String get teamProjectTaskAnswerLabel => 'Tu respuesta';

  @override
  String get teamProjectTaskRunning => 'Trabajando';

  @override
  String get teamProjectTaskWaiting => 'Esperando';

  @override
  String get teamProjectTaskDone => 'Hecha';

  @override
  String get teamProjectTaskFailed => 'La tarea se detuvo antes de terminar';

  @override
  String get teamProjectTaskStale => 'Último estado conocido';

  @override
  String get teamProjectTaskCollapse => 'Colapsar todo';

  @override
  String get teamProjectTaskWork => 'Trabajo completado';

  @override
  String get teamProjectTaskEmpty =>
      'La tarea está en fila. Sus respuestas y revisiones aparecerán aquí.';

  @override
  String get teamProjectTaskReceipt => 'Recibo de promoción';

  @override
  String get teamProjectTaskVerify => 'Verificar la tarea';

  @override
  String get teamProjectTryDemo => 'Probar la demo de AI Team';

  @override
  String get teamProjectLoadFailure =>
      'No se pudo abrir el demo. Los datos guardados de tu proyecto se han conservado.';

  @override
  String get teamProjectDemoDisclosure =>
      'Proyectos simulados. No corren agentes y no cambian repositorios.';

  @override
  String get teamProjectOff => 'Salir del demo';

  @override
  String get teamProjectEditorFixRoundsRange =>
      'Escribe un límite de rondas de arreglo de 0 a 3.';

  @override
  String get teamProjectEditorPositiveTokens =>
      'Escribe un límite de tokens positivo o déjalo vacío.';

  @override
  String get teamProjectEditorReloadConfirmTitle =>
      '¿Actualizar este proyecto?';

  @override
  String get teamProjectEditorReload => 'Actualizar al proyecto más reciente';

  @override
  String get teamProjectEditorDiscardDraft =>
      'Esto reemplaza tus ediciones sin guardar por el proyecto más reciente. Tu proyecto guardado se conserva.';

  @override
  String get teamProjectEditorRoleRequired =>
      'Escribe una nombre para este rol.';

  @override
  String get teamProjectEditorDefaults =>
      'Valores por defecto de un proyecto nuevo';

  @override
  String get teamProjectEditorApplyPlan => 'Aplicar el plan actualizado';

  @override
  String get teamProjectEditorContextFiles => 'Archivos por leer primero';

  @override
  String get teamProjectEditorContextFilesHelp =>
      'Opcional. Una ruta por línea. El equipo los lee antes de planificar. El demo no lee ni sube archivos.';

  @override
  String get teamProjectEditorScreenOff =>
      'Seguir trabajando con la pantalla apagada';

  @override
  String get teamProjectEditorScreenOffHelp =>
      'Esta preferencia se guarda para el proyecto. El trabajo en segundo plano sigue sujeto a los límites del servidor y del sistema.';

  @override
  String get teamProjectEditorDraftApproval =>
      'Los borradores de cambios necesitan tu aprobación antes de volverse la especificación del proyecto.';

  @override
  String get teamProjectEditorChangeRequest =>
      '¿Qué debe cambiar el planificador?';

  @override
  String get teamProjectEditorAskChange => 'Pedir un cambio';

  @override
  String get teamProjectEditorChangeRequired =>
      'Agrega una meta y describe el cambio que quieres.';

  @override
  String get teamProjectTaskCriteria => 'Criterios de aceptación';

  @override
  String get teamProjectTaskOpenFindings => 'Hallazgos abiertos';

  @override
  String get teamProjectTaskFindingsAddressed => 'Hallazgos atendidos';

  @override
  String get teamProjectMergeConfirmBody =>
      'Las ramas de tareas revisadas se fusionarán a dev, seguidas de revisiones combinadas. Main no se toca.';

  @override
  String get teamProjectEditorDraftClearFailed =>
      'Los cambios se guardaron, pero el borrador local no se pudo borrar. Cierra esta hoja y revisa el proyecto antes de reintentar.';

  @override
  String get teamProjectRestartElsewhereConfirmTitle =>
      '¿Empezar de nuevo en otro lado?';

  @override
  String get teamProjectRestartElsewhere => 'Empezar de nuevo en otro lado';

  @override
  String get teamProjectRestartElsewhereBody =>
      'Empieza un intento nuevo en este servidor. La rama anterior se queda en su servidor original.';

  @override
  String get teamProjectWaitForServer => 'Esperar al servidor original';

  @override
  String get teamProjectBudgetNear =>
      'Acercándose a tu presupuesto. El trabajo nuevo se pausa en el límite que elegiste.';

  @override
  String get teamProjectDemoPlanFailure => 'Demo: plan ilegible';

  @override
  String get teamProjectTaskReviewFindings => 'Seleccionar hallazgos abiertos';

  @override
  String get teamProjectTaskResolveAgent => 'Resolver con el agente';

  @override
  String get teamProjectTaskResolveManually => 'Lo resuelvo yo';

  @override
  String get teamProjectTaskRecheckResolution => 'Re-revisar la resolución';

  @override
  String get teamProjectTaskVerificationResults => 'Resultados de verificación';

  @override
  String get teamProjectTaskCriterionMet => 'Cumplido';

  @override
  String get teamProjectTaskCriterionUnmet => 'No cumplido';

  @override
  String get teamProjectTaskCriterionNotApplicable => 'No aplica';

  @override
  String get teamProjectTaskDemoConflict => 'Demo: crear un conflicto';

  @override
  String get teamProjectTaskDemoCommit => 'Demo: agregar un commit a mano';

  @override
  String get teamProjectEditorNoOptions =>
      'Aún no hay opciones disponibles. Vuelve a AI Team para agregar un servidor o un rol.';

  @override
  String get teamProjectEditorUnknownDate => 'Fecha no disponible';

  @override
  String get teamProjectEditorYou => 'Tú';

  @override
  String get teamProjectEditorApprovedBy => 'Aprobado por';

  @override
  String get teamProjectEditorPlanFailed =>
      'El planificador no devolvió un plan utilizable. Deja la meta como una tarea, o pide un plan nuevo.';

  @override
  String get teamProjectEditorUseAsTask => 'Usar como una tarea';

  @override
  String get teamProjectEditorAskAgain => 'Pedir otra vez';

  @override
  String get teamProjectDemoChip => 'Demo';

  @override
  String teamProjectHeadlineMilestone(int current, int total, int working) {
    String _temp0 = intl.Intl.pluralLogic(
      working,
      locale: localeName,
      other: ' · $working en marcha',
      zero: '',
    );
    return 'Hito $current de $total$_temp0';
  }

  @override
  String teamProjectHeadlineDone(int total) {
    return 'Los $total hitos hechos';
  }

  @override
  String teamProjectGoalStatus(String age, int milestones, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones hitos',
      one: '1 hito',
    );
    String _temp1 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos repos',
      one: '1 repo',
    );
    return 'Especificación aprobada hace $age · $_temp0 · $_temp1';
  }

  @override
  String teamProjectGoalStatusDraft(int milestones, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones hitos',
      one: '1 hito',
    );
    String _temp1 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos repos',
      one: '1 repo',
    );
    return 'Borrador de especificación · $_temp0 · $_temp1';
  }

  @override
  String get teamProjectOpenSpec => 'Abrir la especificación';

  @override
  String teamProjectRequestWhere(String role, String server) {
    return '$role en $server';
  }

  @override
  String teamProjectRequestBlocks(String role, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tareas después también esperan',
      one: '1 tarea después también espera',
    );
    return '$role espera; $_temp0';
  }

  @override
  String teamProjectMilestoneTasks(int done, int total) {
    return '$done de $total tareas';
  }

  @override
  String teamProjectMilestoneWaits(int number) {
    return 'Espera a $number';
  }

  @override
  String get teamProjectMilestoneNoTasks => 'Aún no hay tareas';

  @override
  String teamProjectLanesTitle(int busy, int total) {
    return 'Carriles $busy/$total ocupados';
  }

  @override
  String get teamProjectLanesChange => 'Cambiar';

  @override
  String teamProjectLaneRunning(String server, String elapsed) {
    return '$server · hace $elapsed';
  }

  @override
  String teamProjectLaneWaiting(String server) {
    return '$server · esperando un carril libre';
  }

  @override
  String teamProjectLaneTitle(String role, String task) {
    return '$role · $task';
  }

  @override
  String teamProjectLaneNoteParallel(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count carriles',
      one: '1 carril',
    );
    return 'Paralelo · $_temp0';
  }

  @override
  String get teamProjectLaneNoteSingle => 'Un solo carril';

  @override
  String teamProjectLaneNoteDemo(String note) {
    return '$note · cifras simuladas';
  }

  @override
  String teamProjectElapsedSeconds(int count) {
    return '$count s';
  }

  @override
  String teamProjectElapsedMinutes(int count) {
    return '$count min';
  }

  @override
  String teamProjectElapsedHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String teamProjectCostToday(String amount) {
    return '$amount hoy';
  }

  @override
  String teamProjectCostTodayOf(String amount, String limit) {
    return '$amount de $limit hoy';
  }

  @override
  String teamProjectCostTotal(String amount) {
    return '$amount en total';
  }

  @override
  String teamProjectCostTotalOf(String amount, String limit) {
    return '$amount de $limit en total';
  }

  @override
  String get teamProjectCostNotReported => 'Aún no reportado';

  @override
  String teamProjectBoardSummary(int tasks, int milestones) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks tareas',
      one: '1 tarea',
    );
    String _temp1 = intl.Intl.pluralLogic(
      milestones,
      locale: localeName,
      other: '$milestones hitos',
      one: '1 hito',
    );
    return '$_temp0 repartidas en $_temp1';
  }

  @override
  String get teamProjectBoardEmpty => 'Aún no hay tareas';

  @override
  String teamProjectTimelineSummary(int count, String age) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count eventos',
      one: '1 evento',
    );
    return '$_temp0 · el más reciente hace $age';
  }

  @override
  String get teamProjectTimelineEmpty => 'Aún no ha pasado nada';

  @override
  String teamProjectServersSummary(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count servidores',
      one: '1 servidor',
    );
    return '$_temp0';
  }

  @override
  String teamProjectSettingsSummaryParallel(int count) {
    return 'Paralelo · hasta $count carriles';
  }

  @override
  String get teamProjectSettingsSummarySingle => 'Un solo carril';

  @override
  String get teamProjectMenu => 'Menú del proyecto';

  @override
  String get teamProjectTaskMenu => 'Menú de la tarea';

  @override
  String teamProjectDecisionBy(String who, String age) {
    return '$who · hace $age';
  }

  @override
  String get teamProjectYou => 'Tú';

  @override
  String teamProjectPlanFor(int number) {
    return 'Plan para el hito $number · esperándote';
  }

  @override
  String get teamProjectPlanForProject => 'Plan · esperándote';

  @override
  String teamProjectPlanSummary(int phases, int tasks, int repos) {
    String _temp0 = intl.Intl.pluralLogic(
      phases,
      locale: localeName,
      other: '$phases fases',
      one: '1 fase',
    );
    String _temp1 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks tareas',
      one: '1 tarea',
    );
    String _temp2 = intl.Intl.pluralLogic(
      repos,
      locale: localeName,
      other: '$repos repos',
      one: '1 repo',
    );
    return '$_temp0 · $_temp1 · $_temp2';
  }

  @override
  String teamProjectPlanPhase(int number, String title) {
    return 'Fase $number · $title';
  }

  @override
  String get teamProjectPlanReviewPoint => 'Compuerta de revisión · riesgoso';

  @override
  String teamProjectPlanRepo(String name) {
    return 'Repo $name';
  }

  @override
  String teamProjectPlanAfter(int number) {
    return 'después de $number';
  }

  @override
  String teamProjectPlanCriteria(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count criterios',
      one: '1 criterio',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPlanMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '… $count tareas más',
      one: '… 1 tarea más',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPlanEdit => 'Editar el plan';

  @override
  String get teamProjectPlanAsk => 'Pedir un cambio';

  @override
  String get teamProjectPlanNotYet => 'Todavía no';

  @override
  String teamProjectPlanServerTitle(String task) {
    return 'Correr “$task” en';
  }

  @override
  String get teamProjectPlanServerFixed =>
      'Cada tarea corre en la computadora para la que se planificó. Este equipo aún no puede mover tareas a otra computadora.';

  @override
  String teamProjectMergeEffectDev(String repo, int tasks) {
    String _temp0 = intl.Intl.pluralLogic(
      tasks,
      locale: localeName,
      other: '$tasks tareas revisadas',
      one: '1 tarea revisada',
    );
    return '$_temp0 de $repo se fusionarán a dev.';
  }

  @override
  String get teamProjectMergeEffectMain => 'Main no se toca.';

  @override
  String teamProjectReceiptMerged(String repo) {
    return 'Fusionado a dev · $repo';
  }

  @override
  String teamProjectReceiptPromoted(String repo) {
    return 'Promovido a main · $repo';
  }

  @override
  String teamProjectTimelineRepeated(String text, int count) {
    return '$text · $count veces';
  }

  @override
  String get teamProjectPromoteTitle => 'Promover dev → main';

  @override
  String teamProjectPromoteStatus(String repo) {
    return 'Repo $repo · main está protegido. Solo tú puedes promover.';
  }

  @override
  String teamProjectPromoteMilestone(int number, String title) {
    return 'Hito $number · $title';
  }

  @override
  String teamProjectPromoteMerged(int done, int total) {
    return '$done de $total tareas fusionadas';
  }

  @override
  String teamProjectPromoteDays(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count días',
      one: '1 día',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteChecks => 'Revisiones después de la fusión';

  @override
  String get teamProjectPromoteChecksPassed =>
      'Todas las revisiones pasaron después de la última fusión';

  @override
  String get teamProjectPromoteReview => 'Revisión';

  @override
  String teamProjectPromoteAccepted(int number) {
    return 'Aceptaste el hito $number';
  }

  @override
  String get teamProjectPromoteNoReview =>
      'No hizo falta revisión para este trabajo';

  @override
  String teamProjectPromoteChanges(int commits) {
    String _temp0 = intl.Intl.pluralLogic(
      commits,
      locale: localeName,
      other: '$commits commits',
      one: '1 commit',
    );
    return '$_temp0';
  }

  @override
  String teamProjectPromoteFiles(int files) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files archivos',
      one: '1 archivo',
    );
    return '$_temp0';
  }

  @override
  String get teamProjectPromoteSeeChanges => 'Ver los cambios';

  @override
  String get teamProjectPromoteNotYet => 'Todavía no';

  @override
  String teamProjectFindingsTitle(String role, String summary) {
    return 'Revisado por $role · $summary';
  }

  @override
  String get teamProjectEditorGoalLabel => '¿Qué debe lograr el equipo?';

  @override
  String get teamProjectEditorWhereRuns => 'Dónde corre';

  @override
  String get teamProjectEditorMoreOptions => 'Detalles opcionales';

  @override
  String get teamProjectEditorNameHelp =>
      'Opcional. Déjalo vacío para usar el comienzo de la meta.';

  @override
  String get teamProjectEditorBudgetHelp =>
      'El equipo se pausa cuando el día o todo el proyecto alcanza su límite.';

  @override
  String teamProjectFindingsCritical(int count) {
    return '$count críticos';
  }

  @override
  String teamProjectFindingsMajor(int count) {
    return '$count mayores';
  }

  @override
  String teamProjectFindingsMinor(int count) {
    return '$count menores';
  }

  @override
  String get phoneTeamSetupTitle => 'Activar AI Team';

  @override
  String get phoneTeamStepReply => 'Deja que tu respuesta actual termine';

  @override
  String get phoneTeamReplyWaiting => 'Terminando tu respuesta actual…';

  @override
  String get phoneTeamStepStop => 'Detener OpenCode y cerrar las terminales';

  @override
  String get phoneTeamStepCheck =>
      'Revisando que este teléfono sea seguro para el equipo';

  @override
  String get phoneTeamStepServer => 'Iniciar OpenCode otra vez, protegido';

  @override
  String get phoneTeamNotNeeded => 'No necesario';

  @override
  String get phoneTeamStopTitle => '¿Detener OpenCode brevemente?';

  @override
  String get phoneTeamStopBody =>
      'Detiene el servidor de OpenCode en este teléfono por cerca de un minuto y luego lo arranca de nuevo protegido. Las terminales abiertas se cierran.';

  @override
  String get phoneTeamStopConfirm => 'Detener y continuar';

  @override
  String get phoneTeamStopCancel => 'Ahora no';

  @override
  String get phoneTeamStopWaiting => 'Esperando tu respuesta';

  @override
  String get phoneTeamDoneTitle => 'AI Team está listo';

  @override
  String get phoneTeamDoneBody =>
      'Dale una meta y el equipo planifica, construye y revisa el trabajo.';

  @override
  String get phoneTeamFailUnsafeTitle => 'El equipo se queda apagado';

  @override
  String get phoneTeamFailUnsafeBody =>
      'Este teléfono no puede mantener la copia del equipo de tu código separada de los agentes, así que el equipo se queda apagado.';

  @override
  String get phoneTeamFailEngineTitle => 'AI Team no arrancó';

  @override
  String get phoneTeamFailEngineBody =>
      'El motor del equipo no arrancó en este teléfono.';

  @override
  String get phoneTeamFailStopTitle => 'El servidor no se detuvo';

  @override
  String get phoneTeamFailStopBody =>
      'OpenCode o una terminal no se cerraron, así que la revisión aún no puede correr con seguridad.';

  @override
  String get phoneTeamFailServerTitle => 'El servidor no volvió';

  @override
  String get phoneTeamFailServerBody =>
      'Este teléfono pasó la revisión, pero OpenCode no arrancó otra vez. Arráncalo otra vez para terminar.';

  @override
  String get phoneTeamFailNotReadyTitle => 'Aún no listo';

  @override
  String get phoneTeamFailNotReadyBody =>
      'OpenCode volvió, pero el equipo aún no puede empezar el trabajo.';

  @override
  String get phoneTeamFailDeclinedTitle => 'Nada cambió';

  @override
  String get phoneTeamFailDeclinedBody =>
      'OpenCode se queda como está, así que el equipo se queda apagado. Arráncalo otra vez cuando estés listo para reiniciarlo.';

  @override
  String get phoneTeamFailNoServerTitle => 'Agrega primero un servidor';

  @override
  String get phoneTeamFailNoServerBody =>
      'El equipo trabaja con el OpenCode de este teléfono, y aún no hay uno.';

  @override
  String get phoneTeamReasonNotPackaged =>
      'Esta copia de la app no incluye las herramientas de seguridad del equipo.';

  @override
  String get phoneTeamStateBackOn => 'OpenCode volvió a estar encendido.';

  @override
  String get phoneTeamStateStillOff =>
      'OpenCode sigue apagado. Empezar otra vez, o reinícialo desde En este teléfono.';

  @override
  String get phoneTeamStateNotStopped => 'No se detuvo OpenCode.';

  @override
  String get phoneTeamStateTerminalsClosed =>
      'Las terminales abiertas se cerraron.';

  @override
  String get phoneTeamWhyUnsafe =>
      'La revisión que mantiene la copia del equipo de tu código separada de los agentes no pasó.';

  @override
  String get phoneTeamWhyEngine =>
      'El motor del equipo se detuvo o no respondió mientras arrancaba.';

  @override
  String get phoneTeamWhyStop =>
      'OpenCode o una terminal no se cerraron al pedírselo.';

  @override
  String get phoneTeamWhyServer =>
      'OpenCode no respondió después de arrancarse otra vez.';

  @override
  String get phoneTeamWhyNotReady =>
      'El motor del equipo respondió, pero dijo que aún no puede correr trabajo.';

  @override
  String get phoneTeamOffTitle => 'AI Team está apagado';

  @override
  String get phoneTeamOffBody =>
      'Necesita una revisión de seguridad rápida, por ejemplo después de una actualización de la app.';

  @override
  String get phoneTeamBlocked =>
      'El equipo no puede empezar el trabajo hasta que este teléfono sea revisado.';

  @override
  String get phoneTeamStripChecking => 'Revisando AI Team en este teléfono';

  @override
  String get phoneTeamStripWaiting => 'AI Team necesita reiniciar OpenCode';

  @override
  String get phoneTeamStripReview => 'Abre la revisión para elegir cuándo';

  @override
  String phoneTeamStripStep(int step, int total) {
    return 'Paso $step de $total';
  }

  @override
  String get phoneTeamBlockedTitle => '¿Revisar este teléfono primero?';

  @override
  String get teamMigrationTitle => 'AI Team cambió';

  @override
  String get teamMigrationBody =>
      'El nuevo AI Team planifica proyectos completos y los corre en este teléfono. Tu equipo viejo sigue tomando tareas rápidas pero no puede planificar proyectos, y el cambio pregunta antes de detener nada.';

  @override
  String get teamMigrationSwitch => 'Cambiar al nuevo AI Team en este teléfono';

  @override
  String get teamMigrationKeep => 'Conservar el equipo viejo por ahora';

  @override
  String get teamMigrationMenu => 'Qué hay nuevo en AI Team';

  @override
  String get teamProjectPages => 'Páginas del proyecto';

  @override
  String get teamProjectMergeReady => 'Listo para fusionar';

  @override
  String get teamProjectMergeChecking => 'Esperando sus revisiones';

  @override
  String get teamRefusalUnsupportedCommand =>
      'Esta versión del equipo aún no puede hacer eso.';

  @override
  String get teamRefusalUnsupportedCommandNext =>
      'Actualiza la app y reintenta.';

  @override
  String get teamRefusalBoundaryUnverified =>
      'El equipo no puede trabajar hasta que la protección de este teléfono haya sido revisada.';

  @override
  String get teamRefusalBoundaryUnverifiedNext =>
      'Abre AI Team y actívalo otra vez.';

  @override
  String get teamRefusalProtocolUnverified =>
      'El equipo espera que OpenCode se reinicie.';

  @override
  String get teamRefusalProtocolUnverifiedNext => 'Reintenta en un minuto.';

  @override
  String get teamRefusalEngineUnavailable =>
      'El equipo no está respondiendo ahora mismo.';

  @override
  String get teamRefusalEngineUnavailableNext => 'Reintenta en un momento.';

  @override
  String get teamRefusalTransportUncertain =>
      'Puede que el equipo no haya recibido eso.';

  @override
  String get teamRefusalTransportUncertainNext =>
      'Revisa la lista de proyectos antes de reintentar.';

  @override
  String get teamRefusalBusy => 'Otro cambio todavía se está guardando.';

  @override
  String get teamRefusalBusyNext => 'Reintenta en un momento.';

  @override
  String get teamRefusalSaveFailed =>
      'El cambio no se pudo guardar en este teléfono.';

  @override
  String get teamRefusalSaveFailedNext =>
      'Tus ediciones siguen aquí. Reintenta.';

  @override
  String get teamRefusalReadOnly =>
      'El equipo está en solo lectura ahora mismo.';

  @override
  String get teamRefusalReadOnlyNext =>
      'Activa AI Team en este teléfono para hacer cambios.';

  @override
  String get teamRefusalClosed => 'El equipo fue cerrado.';

  @override
  String get teamRefusalClosedNext => 'Abre AI Team otra vez para continuar.';

  @override
  String get teamRefusalCommandRefused => 'El equipo rechazó esta petición.';

  @override
  String get teamRefusalCommandRefusedNext =>
      'Revisa la meta y la carpeta del repositorio y reintenta.';

  @override
  String get teamRefusalImportFailed =>
      'El equipo no pudo leer la carpeta del repositorio.';

  @override
  String get teamRefusalImportFailedNext =>
      'Revisa el nombre de la carpeta y reintenta.';

  @override
  String get teamRefusalPayloadInvalid =>
      'El equipo respondió de una manera que esta app no entiende.';

  @override
  String get teamRefusalPayloadInvalidNext => 'Actualiza la app y reintenta.';

  @override
  String get teamRefusalSchemaUnsupported =>
      'El equipo y esta app están en versiones diferentes.';

  @override
  String get teamRefusalSchemaUnsupportedNext =>
      'Actualiza la app y reintenta.';

  @override
  String get teamRefusalEngineClosed => 'El equipo se está apagando.';

  @override
  String get teamRefusalEngineClosedNext =>
      'Activa AI Team otra vez para continuar.';

  @override
  String get teamRefusalSessionFailed =>
      'El planificador se detuvo antes de responder.';

  @override
  String get teamRefusalSessionFailedNext =>
      'Abre la elección del modelo del Planificador y revísala, y luego pide otra vez.';

  @override
  String get teamRefusalModelNotConfigured =>
      'El modelo para este trabajo no aceptó la petición.';

  @override
  String get teamRefusalModelNotConfiguredNext =>
      'Puede necesitar un inicio de sesión, o puede no ser gratis usarlo aquí. Abre la elección del modelo del Planificador, elige otro modelo y pide otra vez.';

  @override
  String get teamRefusalModelUnavailable =>
      'El modelo elegido no está disponible.';

  @override
  String get teamRefusalModelUnavailableNext =>
      'Elige otro modelo en Ajustes del equipo › Modelo.';

  @override
  String get teamRefusalAuthFailed =>
      'El proveedor del modelo no aceptó el inicio de sesión.';

  @override
  String get teamRefusalAuthFailedNext =>
      'Revisa la clave del proveedor y aprueba la especificación otra vez.';

  @override
  String get teamRefusalCloneFailed =>
      'El equipo no pudo copiar el proyecto para trabajar en él.';

  @override
  String get teamRefusalCloneFailedNext =>
      'Revisa el repositorio del proyecto y aprueba la especificación otra vez.';

  @override
  String get teamRefusalSessionUncertain =>
      'El equipo no está seguro de hasta dónde llegó su última corrida.';

  @override
  String get teamRefusalSessionUncertainNext =>
      'Retómalo si puedes, o aprueba la especificación otra vez.';

  @override
  String get teamRefusalPlanInvalid => 'El plan que volvió no se pudo usar.';

  @override
  String get teamRefusalPlanInvalidNext =>
      'Aprueba la especificación otra vez y el equipo planificará otra vez.';

  @override
  String get teamRefusalNeedsAnswer =>
      'El planificador tiene una pregunta para ti.';

  @override
  String get teamRefusalNeedsAnswerNext =>
      'Abre la especificación y respóndela.';

  @override
  String get teamRefusalRecoveryReview =>
      'El trabajo se detuvo a medio camino y necesita una mirada.';

  @override
  String get teamRefusalRecoveryReviewNext =>
      'Revisa el proyecto y aprueba la especificación otra vez.';

  @override
  String get teamRefusalAppStopped =>
      'La app se cerró antes de que el equipo terminara.';

  @override
  String get teamRefusalAppStoppedNext =>
      'Retómalo para revisar hasta dónde llegó.';

  @override
  String get teamRefusalChatBusy =>
      'El equipo espera a que tu conversación termine de responder.';

  @override
  String get teamRefusalChatBusyNext => 'Después sigue solo.';

  @override
  String get teamRefusalBudgetReached =>
      'El proyecto alcanzó su límite de gasto.';

  @override
  String get teamRefusalBudgetReachedNext =>
      'Sube el límite en los ajustes del proyecto para seguir.';

  @override
  String get teamRefusalModelNotConfiguredAction =>
      'Elegir el modelo del Planificador';

  @override
  String get teamProjectPlanFailedTitle => 'El plan no se hizo';

  @override
  String get teamProjectApproveAgain => 'Aprobar la especificación otra vez';

  @override
  String get teamProjectApproveAgainNote =>
      'Aprueba la especificación otra vez para reintentar.';

  @override
  String get teamProjectTaskWorkLive => 'Trabajo hasta ahora';

  @override
  String get teamProjectTaskWorkLog => 'Bitácora de trabajo';

  @override
  String get teamRefusalDidPlan => 'empezar a planificar';

  @override
  String get teamRefusalDidQuick => 'empezar esa tarea';

  @override
  String get teamRefusalDidApprove => 'empezar el trabajo';

  @override
  String get teamRefusalDidSpec => 'aprobar la especificación';

  @override
  String get teamRefusalDidPromote => 'promover el trabajo';

  @override
  String get teamRefusalDidStop => 'detener el proyecto';

  @override
  String get teamRefusalDidPause => 'pausar el proyecto';

  @override
  String get teamRefusalDidResume => 'reanudar el proyecto';

  @override
  String get teamRefusalDidSave => 'guardar tus cambios';

  @override
  String teamRefusalUnknown(String action) {
    return 'El equipo no pudo $action.';
  }

  @override
  String get teamRefusalUnknownNext =>
      'Reintenta. Si sigue ocurriendo, abre Detalles para ver el código.';

  @override
  String get teamRefusalCode => 'Código';

  @override
  String get phoneTeamProtectedProot =>
      'Protegido por el sandbox de Linux de este teléfono';

  @override
  String get phoneTeamProtectedLandlock =>
      'Protegido por la protección de archivos de Android';

  @override
  String get teamRefusalRepositoryEmpty =>
      'Este repositorio aún no tiene commits.';

  @override
  String get teamRefusalRepositoryEmptyNext =>
      'Haz el primer commit dentro y luego empieza a planificar otra vez.';

  @override
  String get teamRefusalRepositoryLink =>
      'El equipo no pudo copiar este repositorio con seguridad.';

  @override
  String get teamRefusalRepositoryLinkNext =>
      'Reintenta. Si sigue ocurriendo, reporta el problema.';

  @override
  String get teamRefusalRepositoryDamaged =>
      'La copia del repositorio no coincidió con el original.';

  @override
  String get teamRefusalRepositoryDamagedNext =>
      'Reintenta. Si sigue ocurriendo, reporta el problema.';

  @override
  String get teamRefusalPlanTaskName => 'Una tarea del plan no tiene nombre.';

  @override
  String get teamRefusalPlanTaskNameNext =>
      'Pon nombre a cada tarea y aprueba otra vez.';

  @override
  String get teamRefusalPlanPhase => 'Una fase del plan no está completa.';

  @override
  String get teamRefusalPlanPhaseNext =>
      'Revisa que cada fase tenga tareas y criterios, y aprueba otra vez.';

  @override
  String get teamServerPhoneFailed =>
      'AI Team en este teléfono no responde, así que no se puede alcanzar su trabajo.';

  @override
  String get teamServerPhoneNotReady =>
      'AI Team en este teléfono no está listo: su revisión de seguridad no ha pasado.';

  @override
  String get teamServerPhoneNoAnswer =>
      'OpenCode en este teléfono no respondió la última revisión.';

  @override
  String get phoneTeamOffReview => 'Revisar y elegir cuándo';

  @override
  String teamProjectInterruptedRow(String name) {
    return '$name: el trabajo se interrumpió, toca para retomarlo';
  }

  @override
  String get teamProjectResumeUnavailable =>
      'Esta versión de AI Team aún no puede retomar el trabajo interrumpido. Puedes detener el proyecto y empezarlo otra vez.';

  @override
  String get teamProjectInterrupted => 'Interrumpido, listo para retomar';

  @override
  String get teamProjectEditorContextFilesHelpReal =>
      'Opcional. Una ruta por línea. El equipo los lee antes de planificar.';

  @override
  String get addServerCleartextWarning =>
      'Esta dirección de red privada o Tailscale usa HTTP sin cifrar. Tu contraseña y código viajan sin cifrar en la capa HTTP. Úsala solo en una red privada de confianza o con Tailscale; es arriesgado en Wi-Fi público.';

  @override
  String get addServerCleartextConfirm => 'Usar de todos modos';

  @override
  String get addServerCleartextConfirmed =>
      'HTTP simple está activado para esta dirección. Cualquiera en esta red podría leer lo que envías.';

  @override
  String get storageAccessTitle => '¿Permitir el acceso a los archivos?';

  @override
  String get storageAccessBody =>
      'Esta carpeta está en el almacenamiento compartido de tu teléfono. Android oculta los archivos dentro a las apps a menos que permitas el acceso a Todos los archivos.';

  @override
  String get storageAccessWhyScope =>
      'La app lee y cambia archivos solo en las carpetas que abres como proyectos.';

  @override
  String get storageAccessWhyAgent =>
      'El agente lo necesita para trabajar en tu carpeta en su lugar. Sin él solo verías elementos ocultos como .git.';

  @override
  String get storageAccessWhyOff =>
      'Puedes apagarlo en cualquier momento en los Ajustes de Android, en Acceso a todos los archivos.';

  @override
  String get storageAccessAllow => 'Permitir acceso a los archivos';

  @override
  String get storageAccessNotNow => 'Ahora no';

  @override
  String get storageAccessUseAppSpace =>
      'Usar el espacio de proyectos de la app';

  @override
  String get storageAccessRefusedTitle => 'Carpeta no abierta';

  @override
  String get storageAccessRefusedBody =>
      'Sin acceso a los archivos los archivos de esta carpeta no se pueden mostrar, así que no se abrió. Permite el acceso, o elige una carpeta en el espacio de proyectos de la app.';

  @override
  String get filesAccessNeededTitle => 'Archivos ocultos';

  @override
  String get filesAccessNeededBody =>
      'Esta carpeta está en el almacenamiento compartido del teléfono y la app aún no puede leer la mayoría de sus archivos. Solo se muestran los elementos ocultos.';

  @override
  String get storageRestartTitle => '¿Reiniciar para abrir la carpeta?';

  @override
  String get storageRestartBody =>
      'OpenCode en este teléfono debe reiniciarse antes de poder ver esta carpeta. Toma cerca de 30 segundos.';

  @override
  String get storageRestartPause =>
      'Las respuestas en marcha y el trabajo de AI Team se pausan y siguen después.';

  @override
  String get storageRestartBusy =>
      'Una respuesta o una tarea de AI Team corre ahora mismo. Se pausará mientras OpenCode se reinicia.';

  @override
  String get storageRestartConfirm => 'Reiniciar y abrir';

  @override
  String get storageRestartFailedTitle => 'El reinicio no terminó';

  @override
  String get storageRestartFailedBody =>
      'La carpeta no se abrió. Reintenta, o inicia OpenCode desde En este teléfono.';

  @override
  String get kitQueuedChecking => 'Comprobando si tu mensaje llegó…';

  @override
  String get kitQueuedUncertain =>
      'No pudimos confirmar que tu mensaje llegara.';

  @override
  String kitQueuedLastChecked(String time) {
    return 'Última comprobación: $time.';
  }

  @override
  String get kitQueuedStorageFull =>
      'Este teléfono no puede registrar envíos de forma segura ahora, así que tu mensaje no se envió. Se conserva aquí.';

  @override
  String get queuedReceiptConfirmed => 'Mensaje recibido.';

  @override
  String get queuedReceiptStillUncertain =>
      'Aún no se puede confirmar que llegó. No se envió nada otra vez.';

  @override
  String get queuedReceiptStorageProblem =>
      'Este teléfono no puede registrar ni revisar envíos con seguridad ahora mismo. Libera algo de almacenamiento y reintenta.';

  @override
  String get queuedReceiptDetailSentAt => 'Enviado';

  @override
  String get queuedReceiptDetailCommand => 'ID del comando';

  @override
  String get queuedReceiptDetailReceipt => 'ID del recibo';

  @override
  String get queuedReceiptDetailNote =>
      'La conexión terminó antes de que pudiéramos confirmar la recepción.';

  @override
  String get kitToolRetry => 'Intentar de nuevo';

  @override
  String get folderBrowserPlaceLabel => 'Lugar';

  @override
  String get folderBrowserPlaceProjects => 'Espacio de proyectos';

  @override
  String get folderBrowserPlacePhone => 'Este teléfono';

  @override
  String get folderBrowserInternalStorage => 'Almacenamiento interno';

  @override
  String get folderBrowserOpenedBefore => 'Abiertos antes';

  @override
  String get folderBrowserShowHidden => 'Mostrar carpetas ocultas';

  @override
  String get folderBrowserHintEmpty => 'Vacía';

  @override
  String folderBrowserHintMore(String names, int count) {
    return '$names, $count más';
  }

  @override
  String get folderBrowserEmptyPhoneBody => 'Ábrela o sube un nivel.';

  @override
  String folderBrowserNewProjectCreates(String path) {
    return 'Crea $path';
  }

  @override
  String get folderBrowserCreateAndOpen => 'Crear y abrir';

  @override
  String get folderBrowserFoldersLabel => 'Carpetas';

  @override
  String get folderBrowserPhoneRefused =>
      'El almacenamiento del teléfono está desactivado. Permite el acceso para ver aquí.';

  @override
  String get phoneScanTitle => 'Proyectos en este teléfono';

  @override
  String phoneScanStopped(int seconds, int count) {
    return 'Detenido tras $seconds segundos · $count encontrados';
  }

  @override
  String phoneScanFirstShown(int count) {
    return 'Se muestran los primeros $count';
  }

  @override
  String get phoneScanLookDeeper => 'Buscar más a fondo';

  @override
  String get phoneScanEmptyTitle => 'No se encontraron proyectos';

  @override
  String get phoneScanEmptyBody =>
      'Explora carpetas o empieza un proyecto nuevo.';

  @override
  String get phoneScanGit => 'Git';

  @override
  String get phoneScanKindDart => 'Dart';

  @override
  String get phoneScanKindNode => 'Node';

  @override
  String get phoneScanKindPython => 'Python';

  @override
  String get phoneScanKindRust => 'Rust';

  @override
  String get phoneScanKindGo => 'Go';

  @override
  String get phoneScanKindJava => 'Java';

  @override
  String get phoneScanKindRuby => 'Ruby';

  @override
  String get phoneScanKindPhp => 'PHP';

  @override
  String get phoneScanKindDotnet => '.NET';

  @override
  String get phoneScanKindCpp => 'C/C++';

  @override
  String get phoneScanKindGit => 'Carpeta';

  @override
  String get openProjectTitle => 'Abrir un proyecto';

  @override
  String get openProjectSearchPhone => 'Buscar en este teléfono';

  @override
  String get openProjectChooseFolder => 'Elegir una carpeta';

  @override
  String get openProjectChangeFolder => 'Cambiar carpeta';

  @override
  String openProjectUseFolder(String name) {
    return 'Usar $name';
  }

  @override
  String get phoneScanSearching => 'Buscando en este teléfono';

  @override
  String phoneScanChecked(int folders, int found) {
    String _temp0 = intl.Intl.pluralLogic(
      folders,
      locale: localeName,
      other: '$folders carpetas revisadas',
      one: '1 carpeta revisada',
    );
    return '$_temp0 · $found encontrados';
  }

  @override
  String phoneScanDoneIn(int count, String time) {
    return '$count encontrados en $time';
  }

  @override
  String get phoneScanStop => 'Detener';

  @override
  String get openProjectChange => 'Cambiar';

  @override
  String openProjectIn(String folder) {
    return 'En $folder';
  }

  @override
  String get effectsGlowStyle => 'Estilo';

  @override
  String get effectsGlowStyleClassic => 'Clásico';

  @override
  String get effectsGlowStyleSoft => 'Anillo suave';

  @override
  String get effectsGlowColours => 'Colores';

  @override
  String get effectsGlowSpeed => 'Velocidad';

  @override
  String get approvalModeAskDetail => 'Tú respondes a cada solicitud.';

  @override
  String get approvalModeConfirmEverythingBody =>
      'Los agentes de este servidor ejecutarán comandos y cambiarán archivos sin preguntar, en todas las conversaciones.';

  @override
  String get approvalsSheetSubagents => 'Los subagentes siguen esto';

  @override
  String approvalsSheetSetByParent(String name) {
    return 'Definido por $name';
  }

  @override
  String get approvalsSheetSetByServer => 'Definido por este servidor';

  @override
  String get approvalsSheetFootnote =>
      'Se detiene cuando la app se desconecta. Las reglas de denegación del servidor siguen aplicándose.';

  @override
  String get approvalsSheetHistory => 'Aprobado automáticamente';

  @override
  String get chatsHomeTitle => 'Conversaciones';

  @override
  String get chatsHomeAllProjects => 'Todos los proyectos';

  @override
  String get chatsHomeNeedsYou => 'Te necesita';

  @override
  String get chatsHomeRunning => 'En curso';

  @override
  String get chatsHomeRowWorking => 'Trabajando';

  @override
  String get chatsHomeStopped => 'Detenida';

  @override
  String get chatsHomeDone => 'Listo';

  @override
  String get chatsHomeFailed => 'Fallida';

  @override
  String get chatsHomeToday => 'Hoy';

  @override
  String get chatsHomeEarlier => 'Anteriores';

  @override
  String chatsHomeOnlyProject(String project) {
    return 'Mostrando solo las conversaciones de $project. Este servidor no puede listar todos los proyectos.';
  }

  @override
  String get chatsHomeIncomplete =>
      'Algunas conversaciones no se pudieron cargar';

  @override
  String chatsHomeIncompleteNamed(String servers) {
    return '$servers no cargaron';
  }

  @override
  String shellServerPlusOthers(String name, int count) {
    return '$name +$count';
  }

  @override
  String get chatSubagentReadOnly =>
      'Este subagente solo responde en su conversación principal. Escribe allí.';

  @override
  String get chatsHomeOpening => 'Abriendo…';

  @override
  String chatsHomeUnreachable(String servers) {
    return '$servers no responde. No se muestran sus conversaciones.';
  }

  @override
  String get chatsSourcesUnreachable => 'No responde';

  @override
  String get chatsSourcesAll => 'Todas las conexiones';

  @override
  String chatsSourcesSome(int shown, int total) {
    return '$shown de $total conexiones';
  }

  @override
  String get chatsSourcesSheetTitle => 'Conexiones en esta lista';

  @override
  String get chatsSourcesMain => 'Las conversaciones nuevas empiezan aquí';

  @override
  String get chatsSourcesOnPhone => 'En este teléfono';

  @override
  String get chatsSourcesElsewhere => 'En otra computadora';

  @override
  String get chatsSourcesAgents => 'Agentes en este teléfono';

  @override
  String get chatsSourcesAlways => 'Siempre visible';

  @override
  String chatsHomeStillLoading(String agents) {
    return 'Cargando las conversaciones de $agents…';
  }

  @override
  String get chatsHomeEmptyTitle => 'Aún no hay conversaciones';

  @override
  String get chatsHomeNoMatchTitle => 'No hay conversaciones que coincidan';

  @override
  String get chatsHomeNoMatchBody => 'Nada coincide con los filtros elegidos.';

  @override
  String get chatsHomeClearFilters => 'Quitar filtros';

  @override
  String chatsHomeProjectEmptyTitle(String project) {
    return 'No hay conversaciones en $project';
  }

  @override
  String chatsHomeStartChatIn(String project) {
    return 'Empezar una conversación en $project';
  }

  @override
  String get chatsHomeNewChat => 'Nueva conversación';

  @override
  String get chatsHomeOpenFailed =>
      'No se pudo abrir esta conversación. Desliza hacia abajo para actualizar la lista.';

  @override
  String get chatsFilterSheetTitle => 'Mostrar conversaciones de';

  @override
  String chatsFilterChatCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count conversaciones',
      one: '1 conversación',
    );
    return '$_temp0';
  }

  @override
  String chatsFilterRunningCount(int running) {
    return '$running en curso';
  }

  @override
  String get chatsFilterNeedsYouWord => 'te necesita';

  @override
  String get chatsFilterOpenProject => 'Abrir un proyecto…';

  @override
  String get chatsNewSeparateCopy => 'En una copia aparte';

  @override
  String get agentPersonaClaudeCodeStrength => 'Entiende tu base de código';

  @override
  String get agentPersonaClaudeCodeAbout =>
      'La herramienta de código de Anthropic que vive en tu terminal y entiende tu base de código.';

  @override
  String get agentPersonaClaudeCodeNeeds =>
      'Un plan de Claude o una clave de Anthropic';

  @override
  String get agentPersonaCodexStrength => 'Agente de terminal ligero';

  @override
  String get agentPersonaCodexAbout =>
      'El agente de código ligero de OpenAI que corre en tu terminal.';

  @override
  String get agentPersonaCodexNeeds =>
      'Un plan de ChatGPT o una clave de OpenAI';

  @override
  String get agentPersonaGeminiStrength => 'Gemini en tu terminal';

  @override
  String get agentPersonaGeminiAbout =>
      'El agente de código abierto de Google que trae Gemini a tu terminal.';

  @override
  String get agentPersonaGeminiNeeds =>
      'Una cuenta de Google o una clave de Gemini';

  @override
  String get agentPersonaKiloStrength => 'Más de 500 modelos, sin sobreprecio';

  @override
  String get agentPersonaKiloAbout =>
      'Un agente abierto con más de 500 modelos sin sobreprecio, o tus propias claves.';

  @override
  String get agentPersonaKiloNeeds =>
      'Una clave de cualquier proveedor admitido';

  @override
  String get agentPersonaClineStrength => 'El agente abierto';

  @override
  String get agentPersonaClineAbout =>
      'Un agente de código de código abierto para tu editor o tu terminal.';

  @override
  String get agentPersonaClineNeeds =>
      'Una clave de cualquier proveedor admitido';

  @override
  String get agentPersonaOpenCodeStrength =>
      'El agente de código de código abierto';

  @override
  String get agentPersonaOpenCodeAbout =>
      'El agente de código de código abierto: modelos gratis incluidos, o conecta cualquier proveedor.';

  @override
  String get agentPersonaOpenCodeNeeds =>
      'Nada para empezar; agrega una clave para más modelos';

  @override
  String get agentPersonaOtherName => 'Otro agente';

  @override
  String get agentMakerAnthropic => 'de Anthropic';

  @override
  String get agentMakerOpenAI => 'de OpenAI';

  @override
  String get agentMakerGoogle => 'de Google';

  @override
  String get agentMakerOpenSource => 'código abierto';

  @override
  String chatsNewStartWith(String agent) {
    return 'Empezar con $agent';
  }

  @override
  String get chatsNewSetUp => 'Configurar';

  @override
  String get chatsNewPrompt => '¿En qué trabajamos?';

  @override
  String get chatsNewChooseProject => 'Elegir un proyecto';

  @override
  String get chatsNewNeedProject =>
      'Elige un proyecto para empezar una conversación.';

  @override
  String get chatsNewFailed =>
      'La conversación no se inició. Tu mensaje sigue aquí.';

  @override
  String get chatsNewFailedTitle => 'La conversación no empezó';

  @override
  String get chatsNewFailedReportHint =>
      'Reportar un problema incluye estos detalles.';

  @override
  String get shellTabChats => 'Conversaciones';

  @override
  String get shellTabFiles => 'Archivos';

  @override
  String get discoverChatsAliases =>
      'conversaciones sesiones chats trabajo inicio recientes fijadas conversación nueva bandeja actividad te espera aprobaciones permisos preguntas formularios esperando corriendo terminado';

  @override
  String chatProjectChipSemantics(String name) {
    return 'Proyecto $name. Abre un menú con sus archivos, terminal y cambios.';
  }

  @override
  String chatProjectServerChipSemantics(String name, String server) {
    return 'Proyecto $name en $server. Abre un menú con sus archivos, terminal y cambios.';
  }

  @override
  String get chatProjectMenuLabel => 'Herramientas del proyecto';

  @override
  String get agentsChooseTitle => 'Elegir un agente';

  @override
  String get agentsStateReady => 'Listo';

  @override
  String get agentsStateCantReopen =>
      'No puede reabrir conversaciones antiguas';

  @override
  String agentsStateNotInstalled(String size) {
    return 'No instalado · $size';
  }

  @override
  String get agentsStateNotInstalledNoSize => 'No instalado';

  @override
  String get agentsStateSignInNeeded => 'Falta iniciar sesión';

  @override
  String get agentsStateChecking => 'Comprobando el inicio de sesión…';

  @override
  String get agentsStatePhoneCheck => 'Se necesita comprobar el teléfono';

  @override
  String get agentsStateStopped => 'Detenido en segundo plano';

  @override
  String get agentsStateLimit => 'Límite del plan alcanzado';

  @override
  String get agentsStateUnavailable => 'Aún no disponible en este teléfono';

  @override
  String agentsSetupTitle(String agent, String size) {
    return 'Configurar $agent · $size';
  }

  @override
  String agentsSetupTitleNoSize(String agent) {
    return 'Configurar $agent';
  }

  @override
  String agentsSetupBody(String agent) {
    return '$agent se ejecuta en este teléfono. Al instalarlo se descarga el agente y lo que necesita.';
  }

  @override
  String agentsSetupSizeNote(String size) {
    return '$size es el agente en sí. Las partes compartidas pueden agregar más.';
  }

  @override
  String agentsInstallAction(String agent) {
    return 'Instalar $agent';
  }

  @override
  String get agentsCancelSetup => 'Cancelar la configuración';

  @override
  String agentsInstalling(String agent) {
    return 'Instalando $agent…';
  }

  @override
  String get agentsSetupInterrupted =>
      'La configuración se detuvo antes de terminar. Instala de nuevo para continuar.';

  @override
  String get agentsSetupFailed =>
      'La configuración no terminó. Instala de nuevo para intentarlo otra vez.';

  @override
  String get agentsCheckTitle => 'Comprobar este teléfono';

  @override
  String agentsCheckRunning(String agent) {
    return 'Comprobando $agent…';
  }

  @override
  String agentsCheckPassed(String agent) {
    return '$agent está listo en este teléfono.';
  }

  @override
  String agentsCheckFailed(String agent) {
    return '$agent no pasó la comprobación.';
  }

  @override
  String agentsCheckAction(String agent) {
    return 'Comprobar $agent';
  }

  @override
  String get agentsStepInstall => 'Instalado';

  @override
  String get agentsStepVersion => 'Versión';

  @override
  String get agentsStepConnection => 'Conexión';

  @override
  String get agentsStepReady => 'Listo';

  @override
  String get agentsFailUnavailable =>
      'Este teléfono aún no puede ejecutar este agente.';

  @override
  String agentsResumeFailed(String agent) {
    return '$agent no arrancó otra vez. Toca Retomar para probar una vez más. Si aún no arranca, corre su revisión del teléfono.';
  }

  @override
  String get agentsFailStorage =>
      'No se pudo guardar esta configuración. Libera algo de espacio y ejecútala de nuevo.';

  @override
  String get agentsFailInstall =>
      'El agente no se instaló. Instálalo de nuevo.';

  @override
  String get agentsFailInterrupted =>
      'La configuración se detuvo antes de terminar. Instálalo de nuevo.';

  @override
  String get agentsFailVersion =>
      'El agente instalado no pasó la comprobación de versión.';

  @override
  String get agentsFailDaemon => 'La conexión del agente no arrancó.';

  @override
  String get agentsFailHello =>
      'Paseo no confirmó la conexión. Reintenta para reiniciarla y comprobar de nuevo.';

  @override
  String get agentsFailArchitecture =>
      'Este agente no coincide con el procesador de este teléfono.';

  @override
  String get agentsFailStale =>
      'Esta comprobación está desactualizada. Ejecútala de nuevo.';

  @override
  String get agentsFailBusy =>
      'Hay otra configuración en curso. Espera a que termine.';

  @override
  String agentsSignInTitle(String agent) {
    return 'Iniciar sesión con $agent';
  }

  @override
  String agentsSignInIntro(String agent) {
    return '$agent inicia sesión con sus propios pasos, con tu propia cuenta de Claude y según los términos de Anthropic. La app nunca ve tu inicio de sesión.';
  }

  @override
  String agentsSignInTerminalIntro(String agent) {
    return '$agent abre su página de inicio de sesión. Inicia sesión allí con tu cuenta de Claude y vuelve. Si $agent pide un código, cópialo en la página y elige Pegar.';
  }

  @override
  String agentsSignInIntroOther(String agent) {
    return '$agent inicia sesión con sus propios pasos, con tu propia cuenta de su proveedor. La app nunca ve tu inicio de sesión.';
  }

  @override
  String agentsSignInTerminalIntroOther(String agent) {
    return '$agent inicia sesión aquí con sus propios pasos. Si abre una página, inicia sesión allí y vuelve. Si pide un código o una clave, elige Pegar.';
  }

  @override
  String get agentsSignInAgain => 'Iniciar sesión de nuevo';

  @override
  String agentsSignInTerminalNotYet(String agent) {
    return '$agent aún no ha iniciado sesión. Vuelve a iniciar el proceso cuando quieras.';
  }

  @override
  String get agentsSignInStopFailed =>
      'No se pudo detener el inicio de sesión. Mantén la app abierta e inténtalo de nuevo.';

  @override
  String get agentsSignInChecking => 'Comprobando el inicio de sesión…';

  @override
  String agentsSignInStart(String agent) {
    return 'Iniciar sesión con $agent';
  }

  @override
  String get agentsSignedIn => 'Sesión iniciada';

  @override
  String agentsSignedInBody(String agent) {
    return '$agent está listo para conversaciones nuevas.';
  }

  @override
  String agentsSignInDone(String agent) {
    return 'Usar $agent';
  }

  @override
  String agentsSignedInAs(String account) {
    return 'Sesión iniciada como $account';
  }

  @override
  String agentsSignOutAction(String agent) {
    return 'Cerrar sesión en $agent';
  }

  @override
  String agentsSignOutTitle(String agent) {
    return '¿Cerrar sesión en $agent?';
  }

  @override
  String agentsSignOutBody(String agent) {
    return '$agent no podrá iniciar conversaciones nuevas hasta que vuelvas a iniciar sesión.';
  }

  @override
  String agentsSignOutKept(String agent) {
    return 'Tus conversaciones con $agent se conservan.';
  }

  @override
  String agentsSignOutFailed(String agent) {
    return 'No se pudo confirmar que $agent cerró sesión. Inténtalo de nuevo.';
  }

  @override
  String agentsRemoveAction(String agent) {
    return 'Quitar $agent';
  }

  @override
  String agentsRemoveTitle(String agent) {
    return '¿Quitar $agent?';
  }

  @override
  String get agentsRemoveBody =>
      'Esto quita el agente instalado de este teléfono. Tus cuentas y conversaciones se conservan, y puedes instalarlo de nuevo.';

  @override
  String agentsRemoving(String agent) {
    return 'Quitando $agent…';
  }

  @override
  String agentsRemoved(String agent, String size) {
    return '$agent quitado. Se liberaron $size.';
  }

  @override
  String agentsAlreadyRemoved(String agent) {
    return '$agent ya está quitado.';
  }

  @override
  String get agentsRemoveUnsupported => 'Este agente no se puede quitar aquí.';

  @override
  String get agentsRemoveBusy =>
      'Este agente está en uso. Espera a que termine su trabajo e inténtalo de nuevo.';

  @override
  String get agentsRemoveUnconfirmed =>
      'No se pudo confirmar que el agente se quitó. Comprueba este teléfono e inténtalo de nuevo.';

  @override
  String get agentsDone => 'Listo';

  @override
  String get agentsModelTitle => 'Elegir un modelo';

  @override
  String get agentsModelDefault => 'Modelo predeterminado';

  @override
  String get agentsModelLoading => 'Leyendo los modelos…';

  @override
  String get agentsSignInBadCode =>
      'Ese código está incompleto. Copia el código completo del navegador (tiene un # en medio) y pégalo de nuevo.';

  @override
  String get agentsSignInFailed =>
      'El inicio de sesión no terminó. Vuelve a iniciarlo.';

  @override
  String agentsSignInRejected(String agent) {
    return '$agent no aceptó ese código. Puede que haya caducado o ya se haya usado. Toca Obtener un código nuevo.';
  }

  @override
  String get agentsSignInHostDown =>
      'El agente no está en ejecución. Reanúdalo y luego inicia sesión.';

  @override
  String get agentsSignInUnavailable =>
      'El inicio de sesión aún no está listo en este teléfono';

  @override
  String agentsSignInLimit(String agent) {
    return 'Límite del plan de $agent alcanzado · inténtalo más tarde';
  }

  @override
  String agentsLimitReset(String agent, String time) {
    return 'Límite del plan de $agent alcanzado · se restablece $time';
  }

  @override
  String agentsLimitUnknown(String agent) {
    return 'Se alcanzó el límite del plan de $agent · inténtalo más tarde';
  }

  @override
  String agentsSignedOutLine(String agent) {
    return '$agent cerró sesión';
  }

  @override
  String agentsSignInAction(String agent) {
    return 'Iniciar sesión en $agent';
  }

  @override
  String agentsStoppedLine(String agent) {
    return '$agent se detuvo en segundo plano';
  }

  @override
  String agentsResumeAction(String agent) {
    return 'Reanudar $agent';
  }

  @override
  String get agentsResumeNoticeTitle => '¿Empezar una conversación nueva?';

  @override
  String agentsResumeNoticeBody(String agent) {
    return 'Empieza una conversación nueva. $agent no puede reabrir esta, y se queda como está.';
  }

  @override
  String get agentsStartNew => 'Empezar conversación nueva';

  @override
  String get agentsCancel => 'Cancelar';

  @override
  String get agentsRestartLine =>
      'Cierra y vuelve a abrir la app para terminar de borrar los inicios de sesión.';

  @override
  String get agentsRestartAction => 'Cerrar la app';

  @override
  String get agentsSectionTitle => 'Agentes';

  @override
  String get agentsRunOnBuiltIn =>
      'Los agentes se ejecutan en el servidor integrado';

  @override
  String get agentsSwitchToBuiltIn => 'Cambiar al servidor integrado';

  @override
  String get agentsChecking => 'Buscando agentes en este teléfono…';

  @override
  String get agentsStateNeedsArm => 'Requiere un teléfono de 64 bits';

  @override
  String get agentsStateNoDownload => 'Aún no hay descarga verificada';

  @override
  String get agentsInstallHint => 'Instalar';

  @override
  String get agentsActionFailed =>
      'Eso no funcionó en este teléfono. Abre Detalles para ver por qué.';

  @override
  String get chatsHomeOtherFolders => 'Otras carpetas';

  @override
  String get agentsSignInPaste => 'Pegar';

  @override
  String get chatRequestAnswerAllowed => 'Permitido';

  @override
  String get chatRequestAnswerRejected => 'Rechazado';

  @override
  String agentCardAsks(String agent) {
    return '$agent pregunta';
  }

  @override
  String agentCardReports(String agent) {
    return '$agent reporta';
  }

  @override
  String agentCardAsksAnnouncement(String agent, String title) {
    return '$agent pregunta: $title';
  }

  @override
  String get agentCardSend => 'Enviar';

  @override
  String get agentCardConfirmDefault => 'Sí, adelante';

  @override
  String get agentCardCancelDefault => 'Cancelar';

  @override
  String get agentCardChoosePrompt => 'Elige una opción para enviar.';

  @override
  String get agentCardChooseOneOrMore =>
      'Elige al menos una opción para enviar.';

  @override
  String get agentCardFormFix => 'Llena los campos obligatorios para enviar.';

  @override
  String get agentCardFieldRequired => 'Este campo es obligatorio.';

  @override
  String get agentCardFieldNumber => 'Escribe un número.';

  @override
  String agentCardFieldMin(String min) {
    return 'Escribe $min o más.';
  }

  @override
  String agentCardFieldMax(String max) {
    return 'Escribe $max o menos.';
  }

  @override
  String get agentCardTextNote =>
      'El agente lo ve, y se guarda en la conversación.';

  @override
  String get agentCardBusy =>
      'El agente todavía está trabajando. Puedes responder cuando se detenga.';

  @override
  String get agentCardSending => 'Enviando tu respuesta…';

  @override
  String get agentCardUnknown =>
      'Aún no podemos saber si esta tarjeta fue respondida. Se actualiza cuando lo hace la conversación.';

  @override
  String get agentCardDeliveryUnknown =>
      'No pudimos confirmar que tu respuesta llegó. Revisa la conversación antes de responder otra vez.';

  @override
  String get agentCardFailed =>
      'Tu respuesta no pasó. No se envió nada, y puedes reintentar.';

  @override
  String get agentCardSent => 'Enviado';

  @override
  String get agentCardShow => 'Mostrar la tarjeta';

  @override
  String get agentCardNotAnswered => 'Sin responder';

  @override
  String get agentCardUnreadable => 'Esta tarjeta no se pudo mostrar';

  @override
  String get agentCardDetails => 'Detalles';

  @override
  String get agentCardComposerHint => 'O escribe tu respuesta';

  @override
  String get agentCardDangerTitle => '¿Enviar esta respuesta?';

  @override
  String get agentCardDangerBody =>
      'Tu respuesta va al agente como un mensaje. El agente decide qué hacer con ella.';

  @override
  String get agentCardPhotoTake => 'Tomar una foto';

  @override
  String get agentCardPhotoChoose => 'Elegir una foto';

  @override
  String agentCardPhotoRemove(int number) {
    return 'Quitar la foto $number';
  }

  @override
  String agentCardPhotoName(int number) {
    return 'Foto $number';
  }

  @override
  String agentCardPhotoCount(int count, int max) {
    return '$count de $max';
  }

  @override
  String get agentCardPhotoMax =>
      'Eso es todo lo que esta tarjeta toma de fotos.';

  @override
  String get agentCardPhotoNone => 'Agrega una foto para enviar.';

  @override
  String get agentCardPhotoUnavailable =>
      'No se pueden agregar fotos desde aquí. Escribe tu respuesta en su lugar.';

  @override
  String get agentCardPhotoTooLarge =>
      'Las fotos pueden ser de hasta 10 MB cada una y 20 MB entre todas.';

  @override
  String get agentCardChartBar => 'Gráfico de barras';

  @override
  String get agentCardChartLine => 'Gráfico de líneas';

  @override
  String agentCardChartSeries(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count series',
      one: '1 serie',
    );
    return '$_temp0';
  }

  @override
  String agentCardChartPoints(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count puntos',
      one: '1 punto',
    );
    return '$_temp0';
  }

  @override
  String agentCardChartHighest(String where, String value) {
    return 'el más alto $where: $value';
  }

  @override
  String get agentCardChartNone => 'sin datos';

  @override
  String agentCardTableLabel(int columns, int rows) {
    return 'Tabla, $columns columnas, $rows filas';
  }

  @override
  String get agentCardDiffFile => 'Archivo';

  @override
  String get agentCardDiffAdded => 'Agregado';

  @override
  String get agentCardDiffRemoved => 'Eliminado';

  @override
  String agentCardDiffMore(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'y $count archivos más',
      one: 'y 1 archivo más',
    );
    return '$_temp0';
  }

  @override
  String agentCardProgressValue(int percent) {
    return '$percent%';
  }

  @override
  String get cardsFromAgentsTitle => 'Tarjetas de los agentes';

  @override
  String get cardsFromAgentsSupporting =>
      'Los agentes pueden mostrarte opciones, formularios e informes como tarjetas, y pedir una foto.';

  @override
  String get cardsStatusOff => 'Apagado';

  @override
  String get cardsStatusChecking => 'Revisando este teléfono…';

  @override
  String cardsStatusOn(String agents) {
    return 'Encendido para $agents';
  }

  @override
  String cardsStatusPartial(String agents, String reason) {
    return 'Encendido para $agents. $reason';
  }

  @override
  String cardsStatusRestart(String agents) {
    return 'Reinicia a $agents para terminar de activar esto.';
  }

  @override
  String cardsStatusUnavailable(String reason) {
    return 'No disponible. $reason';
  }

  @override
  String cardsStatusFailed(String reason) {
    return 'No se pudo activar esto. $reason';
  }

  @override
  String get cardsSettingFailed => 'No se pudo cambiar este ajuste. Reintenta.';

  @override
  String get cardsAgentClaude => 'Claude Code';

  @override
  String get cardsProblemUnsupportedHost =>
      'Los agentes en este servidor no pueden mostrar tarjetas.';

  @override
  String get cardsProblemRuntimeMissing =>
      'Las herramientas que las tarjetas necesitan aún no están en este teléfono.';

  @override
  String get cardsProblemNotQualified =>
      'Las tarjetas aún no se han revisado para funcionar con los agentes de aquí.';

  @override
  String get cardsProblemPermissionDenied =>
      'Este teléfono no permitiría el cambio.';

  @override
  String get cardsProblemConflict =>
      'Otra configuración ya usa el mismo nombre. No se cambió nada.';

  @override
  String get cardsProblemInstallationFailed =>
      'No se pudieron instalar las tarjetas.';

  @override
  String get cardsProblemRegistrationFailed =>
      'No se pudo avisar al agente de las tarjetas.';

  @override
  String get cardsProblemVerificationFailed =>
      'Las tarjetas se instalaron, pero no pasaron la revisión.';

  @override
  String get cardsProblemRemovalFailed =>
      'No se pudieron quitar las tarjetas por completo. Reintenta después de reiniciar al agente.';

  @override
  String get cardsProblemStorageFailed =>
      'El teléfono no pudo guardar el ajuste.';

  @override
  String get cardsProblemBusy =>
      'El teléfono está ocupado con otra cosa. Reintenta en un momento.';

  @override
  String get agentCardFieldRequiredHint => 'Obligatorio';

  @override
  String get agentCardYourAnswer => 'Tu respuesta';

  @override
  String get agentCardDangerConfirm => 'Enviar la respuesta';

  @override
  String get agentsSignInOpenPage => 'Abrir página de inicio de sesión';

  @override
  String agentsSignInCopyCode(String code) {
    return 'Copiar código $code';
  }

  @override
  String cardsProblemNotQualifiedFor(int count, String agents) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'A $agents aún no se les ha revisado que funcionen con tarjetas.',
      one: 'A $agents aún no se le ha revisado que funcione con tarjetas.',
    );
    return '$_temp0';
  }

  @override
  String cardsProblemRuntimeMissingFor(String agents) {
    return 'Instala primero $agents en este teléfono.';
  }

  @override
  String cardsProblemRegistrationFailedFor(String agents) {
    return 'No se pudo avisar a $agents de las tarjetas.';
  }

  @override
  String cardsProblemVerificationFailedFor(String agents) {
    return 'Se instalaron tarjetas para $agents, pero no pasaron la revisión.';
  }

  @override
  String cardsProblemRemovalFailedFor(String agents) {
    return 'No se pudieron quitar las tarjetas de $agents por completo. Reintenta después de un reinicio.';
  }

  @override
  String get crashReportsLabel => 'Reportes de caídas';

  @override
  String get crashReportsSwitch =>
      'Guardar reportes de caídas en este teléfono';

  @override
  String get crashReportsSwitchBody =>
      'Se conservan en este teléfono. Nunca se envían automáticamente.';

  @override
  String get crashReportsUnavailable =>
      'Los reportes de caídas no están disponibles ahora. Reinicia la app y reintenta.';

  @override
  String get crashReportsFailed =>
      'No se pudieron actualizar los reportes de caídas. Reinicia la app y reintenta.';

  @override
  String get crashReportsNone => 'Aún no hay reportes de caídas';

  @override
  String get crashReportsNoneBody =>
      'Uno aparece aquí si la app se cierra o deja de responder.';

  @override
  String get crashKindError => 'La app chocó con un error inesperado';

  @override
  String get crashKindScreen => 'No se pudo mostrar una pantalla';

  @override
  String get crashKindClosed => 'La app se cerró inesperadamente';

  @override
  String get crashKindNotResponding => 'La app dejó de responder';

  @override
  String get crashReportPreviewBody =>
      'Se conserva solo en este teléfono. Se envía únicamente si tú incluyes los errores guardados en un reporte de problema.';

  @override
  String get crashReportDetailSource => 'Fuente';

  @override
  String get crashReportDetailCategory => 'Categoría';

  @override
  String crashReportsDelete(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Eliminar $count reportes de caída guardados',
      one: 'Eliminar 1 reporte de caída guardado',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsDeleteTitle =>
      '¿Eliminar los reportes de caída guardados?';

  @override
  String get crashReportsDeleteBody =>
      'Elimina los reportes de caída guardados. Guardar reportes de caídas sigue encendido.';

  @override
  String get crashReportsDeleteConfirm => 'Eliminar los reportes de caídas';

  @override
  String get crashReportsNote =>
      'Solo se conserva qué clase de problema ocurrió y cuándo: sin mensajes de error, conversaciones ni contraseñas. Conserva los últimos 20 reportes.';

  @override
  String crashReportsOnClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Activar esto borra los $count errores guardados de arriba.',
      one: 'Activar esto borra el error guardado de arriba.',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsOffDeletes =>
      'Apagar esto elimina los reportes de caídas guardados.';

  @override
  String crashReportsOffDeletesAndClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Apagar esto elimina los reportes de caídas guardados y borra los $count errores guardados de arriba.',
      one:
          'Apagar esto elimina los reportes de caídas guardados y borra el error guardado de arriba.',
    );
    return '$_temp0';
  }

  @override
  String crashReportsOffClears(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Apagar esto borra los $count errores guardados de arriba.',
      one: 'Apagar esto borra el error guardado de arriba.',
    );
    return '$_temp0';
  }

  @override
  String crashReportsDeleteBodyWithErrors(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          'Elimina los reportes de caídas y borra los $count errores guardados de arriba. Guardar reportes de caídas sigue encendido.',
      one:
          'Elimina los reportes de caídas y borra el error guardado de arriba. Guardar reportes de caídas sigue encendido.',
    );
    return '$_temp0';
  }

  @override
  String get crashReportsClearedToo =>
      'Los reportes de caídas guardados también se eliminan.';

  @override
  String get recentErrorScreen => 'No se pudo dibujar una pantalla';

  @override
  String get recentErrorConnection =>
      'Se perdió la conexión en vivo con el servidor';

  @override
  String get recentErrorAndroidExit => 'Android cerró la app';

  @override
  String get recentErrorTemperature => 'Cambió la temperatura del teléfono';

  @override
  String get recentErrorStartup => 'La app tuvo problemas al arrancar';

  @override
  String get recentErrorReportStore =>
      'No se pudieron abrir los reportes de problemas guardados';

  @override
  String get recentErrorGeneric => 'Algo salió mal';

  @override
  String get chatUiToolBackgroundTaskFinished => 'Terminó la tarea de fondo';

  @override
  String get chatUiToolBackgroundTaskFailed => 'Falló la tarea de fondo';

  @override
  String get chatUiToolBackgroundTaskEnded => 'La tarea de fondo terminó';

  @override
  String get chatUiToolBackgroundTaskStopped => 'Se detuvo la tarea de fondo';

  @override
  String chatUiToolMessageTo(String name) {
    return 'Mensaje a $name';
  }

  @override
  String get chatUiToolMessageToMain => 'Mensaje a la conversación principal';

  @override
  String get chatUiToolMessageToEveryone => 'Mensaje a todo el equipo';

  @override
  String get chatUiToolMessageToAgent => 'Mensaje a un agente';

  @override
  String get toolCardMessageRecipientId => 'ID del destinatario';

  @override
  String chatUiToolBackgroundNamedFinished(String name) {
    return '$name terminó';
  }

  @override
  String chatUiToolBackgroundNamedFailed(String name) {
    return '$name falló';
  }

  @override
  String chatUiToolBackgroundNamedStopped(String name) {
    return '$name se detuvo';
  }

  @override
  String chatUiToolBackgroundNamedEnded(String name) {
    return '$name terminó';
  }

  @override
  String chatUiToolBackgroundNamedCutOff(String name) {
    return '$name se detuvo al terminar la última conversación';
  }

  @override
  String get chatUiToolBackgroundTaskCutOff =>
      'La tarea de fondo se detuvo al terminar la última conversación';

  @override
  String get chatUiToolShowCard => 'Mostrar la tarjeta';

  @override
  String get chatUiToolShowedCard => 'Mostró una tarjeta';

  @override
  String get chatUiToolCardFailed => 'No se pudo mostrar esta tarjeta';

  @override
  String get chatStallModelSlow =>
      'El modelo está tardando más de lo normal. Espera, o detén la respuesta e inténtalo de nuevo.';

  @override
  String get chatStallHelperDown =>
      'El ayudante del agente en este teléfono se detuvo. Detén la respuesta e inténtalo de nuevo.';

  @override
  String get chatStallConnectionLost =>
      'Se perdió la conexión con el agente. Detén la respuesta e inténtalo de nuevo cuando vuelva.';

  @override
  String get chatStallConnectionUnchecked =>
      'No ha llegado nada en un rato y no se pudo comprobar la conexión. Espera, o detén la respuesta e inténtalo de nuevo.';

  @override
  String connectionSwitchingTo(String target) {
    return 'Cambiando a $target…';
  }

  @override
  String get shellServerSwitching => 'Cambiando…';

  @override
  String get agentsStateNotCertified => 'Aún no certificado en esta versión';

  @override
  String get agentsChipSignIn => 'Iniciar sesión';

  @override
  String get agentsChipResume => 'Reanudar';

  @override
  String get agentsChipCheck => 'Comprobar';

  @override
  String agentNamesPair(String first, String second) {
    return '$first y $second';
  }

  @override
  String get diagnosticsExitHistoryTitle => 'Salidas recientes de la app';

  @override
  String get diagnosticsExitHistoryNote =>
      'Android registra cada vez que la app se cierra. Nada de aquí se envía automáticamente.';

  @override
  String get diagnosticsExitHistoryUnsupported =>
      'No disponible en este teléfono';

  @override
  String get diagnosticsExitHistoryUnsupportedBody =>
      'Android 11 y posteriores conservan este registro.';

  @override
  String get diagnosticsExitHistoryFailed =>
      'No se pudieron leer las salidas recientes de la app. Reintenta, o abre la app de nuevo.';

  @override
  String get diagnosticsExitNormal => 'La app se cerró';

  @override
  String get diagnosticsExitUpdate => 'La app se actualizó';

  @override
  String get diagnosticsExitForceStop => 'La app se detuvo';

  @override
  String get diagnosticsExitLowMemory => 'El teléfono necesitó memoria';

  @override
  String get diagnosticsExitCrash => 'La app se detuvo inesperadamente';

  @override
  String get diagnosticsExitKilled => 'Android terminó la app';

  @override
  String get diagnosticsExitReasonCode => 'Código de la razón';

  @override
  String get diagnosticsExitImportance => 'Importancia';

  @override
  String get diagnosticsExitSummary => 'Resumen';

  @override
  String get diagnosticsReportOpen =>
      'Compartir los reportes de caídas guardados';

  @override
  String get diagnosticsReportTitle => 'Previsualizar el reporte de caída';

  @override
  String get diagnosticsReportPrivacy =>
      'Solo categorías de error y horas, sin mensajes ni conversaciones. Nada sale de este teléfono hasta que toques Compartir el reporte.';

  @override
  String diagnosticsReportSize(int count, String size) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reportes · $size',
      one: '1 reporte · $size',
    );
    return '$_temp0';
  }

  @override
  String get diagnosticsReportShare => 'Compartir el reporte';

  @override
  String get diagnosticsReportStale =>
      'Los detalles guardados cambiaron. Revisa el reporte nuevo antes de compartirlo.';

  @override
  String get diagnosticsReportShareFailed =>
      'No se pudo abrir el compartir. Reintenta.';

  @override
  String get diagnosticsReportCaptureOff =>
      'Los reportes de caídas guardados están apagados. Actívalos para guardar los errores futuros de la app.';

  @override
  String get diagnosticsReportEmpty =>
      'No hay reportes de caídas guardados por compartir.';

  @override
  String get diagnosticsUnavailable =>
      'Estos detalles no están disponibles. Abre la app de nuevo y reintenta.';

  @override
  String backgroundPauseTimeLimit(String at) {
    return 'Conexión en segundo plano en pausa $at para ahorrar batería.';
  }

  @override
  String get backgroundPauseRestricted =>
      'Conexión en segundo plano en pausa: el uso de batería está restringido.';

  @override
  String backgroundPauseUserStopped(String at) {
    return 'La conexión en segundo plano se detuvo al cerrar la app $at.';
  }

  @override
  String backgroundPauseAppUpdated(String at) {
    return 'La conexión en segundo plano se detuvo al actualizar la app $at.';
  }

  @override
  String get backgroundPauseInterrupted =>
      'La conexión en segundo plano se detuvo por un motivo desconocido.';

  @override
  String get backgroundPauseDeviceRestarted =>
      'La conexión en segundo plano se detuvo cuando el teléfono se reinició.';

  @override
  String get backgroundPauseResume => 'Reanudar la conexión en segundo plano';

  @override
  String get backgroundPauseResumeShort => 'Reanudar';

  @override
  String get backgroundPauseResuming =>
      'Reanudando la conexión en segundo plano…';

  @override
  String get backgroundPauseResumeFailed =>
      'No se pudo reanudar. Abre los ajustes de Mantener en ejecución.';

  @override
  String get backgroundPauseOpenKeepRunning =>
      'Abrir ajustes de Mantener en ejecución';

  @override
  String get backgroundPauseResumed => 'Conexión en segundo plano reanudada.';

  @override
  String get diagnosticsExitNoProblems =>
      'No hubo cierres inesperados recientes.';

  @override
  String diagnosticsExitRoutine(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count cierres rutinarios (actualizaciones, lo cerraste tú)',
      one: '1 cierre rutinario (actualizaciones, lo cerraste tú)',
    );
    return '$_temp0';
  }

  @override
  String diagnosticsExitShowAll(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Mostrar los $count todos',
      one: 'Mostrar el 1 todo',
    );
    return '$_temp0';
  }

  @override
  String get diagnosticsExitUnknown => 'Se cerró por una razón desconocida';

  @override
  String diagnosticsExitToday(String time) {
    return 'Hoy $time';
  }

  @override
  String diagnosticsExitYesterday(String time) {
    return 'Ayer $time';
  }

  @override
  String get liveToolCommand => 'Ejecutando un comando…';

  @override
  String get liveToolEdit => 'Editando archivos…';

  @override
  String get liveToolRead => 'Leyendo archivos…';

  @override
  String get liveToolSearch => 'Buscando en los archivos…';

  @override
  String get liveToolWeb => 'Navegando por la web…';

  @override
  String get liveToolSubagent => 'Corriendo un sub-agente…';

  @override
  String get liveToolPlan => 'Planificando…';

  @override
  String get liveToolWorking => 'Trabajando…';

  @override
  String get liveToolCard => 'Mostrando una tarjeta…';

  @override
  String get liveToolBackgroundTask => 'Revisando una tarea de fondo…';

  @override
  String liveToolOther(String tool) {
    return 'Corriendo $tool…';
  }

  @override
  String get e7LocaleUiJapanese => '日本語';

  @override
  String get e7LocaleUiChinese => '简体中文';

  @override
  String get e7LocaleUiSpanish => 'Español';

  @override
  String get e7LocaleUiPortuguese => 'Português (Brasil)';

  @override
  String get e7LocaleUiRussian => 'Русский';

  @override
  String connectorCardSuggests(String agent) {
    return '$agent sugiere un conector';
  }

  @override
  String connectorCardAnnouncement(String agent, String name) {
    return '$agent sugiere un conector: $name';
  }

  @override
  String get connectorCardRuns => 'Corre';

  @override
  String get connectorCardLasts => 'Dura';

  @override
  String get connectorCardLastsValue => 'Mientras este servidor corra';

  @override
  String get connectorCardRunsNode => 'En este servidor, con Node';

  @override
  String get connectorCardRunsPython => 'En este servidor, con Python';

  @override
  String get connectorCardRunsDocker =>
      'En Docker, que la app no puede arrancar';

  @override
  String get connectorCardRunsNone => 'Nada que la app pueda arrancar';

  @override
  String get connectorCardRunsHosted => 'Alojado';

  @override
  String get connectorCardConnect => 'Conectar';

  @override
  String get connectorCardConnecting => 'Conectando…';

  @override
  String get connectorCardChecking => 'Revisando la conexión…';

  @override
  String get connectorCardSignInNeeded =>
      'Este conector necesita que inicies sesión.';

  @override
  String get connectorCardSignIn => 'Iniciar sesión';

  @override
  String get connectorCardOpenSignIn => 'Abrir la página de inicio de sesión';

  @override
  String get connectorCardSignInWaiting =>
      'Esperando que el inicio de sesión termine en tu navegador.';

  @override
  String get connectorCardManualCode =>
      'Pega la URL de retorno del navegador o el código de autorización para terminar el inicio de sesión.';

  @override
  String get connectorCardCodeLabel => 'URL de retorno o código';

  @override
  String get connectorCardCodeRequired =>
      'Pega primero la URL de retorno o el código.';

  @override
  String get connectorCardFinishSignIn => 'Terminar el inicio de sesión';

  @override
  String get connectorCardCheckStatus => 'Actualizar';

  @override
  String get connectorCardCancelSignIn => 'Cancelar el inicio de sesión';

  @override
  String get connectorCardCancelTitle => '¿Cancelar el inicio de sesión?';

  @override
  String connectorCardCancelBody(String name) {
    return '$name deja de esperar, y su inicio de sesión guardado se borra. Tendrías que iniciar sesión otra vez para usarlo.';
  }

  @override
  String get connectorCardKeepWaiting => 'Seguir esperando';

  @override
  String get connectorCardConnected => 'Conectado';

  @override
  String get connectorCardToolsReady => 'Herramientas listas';

  @override
  String get connectorCardLoadedTools =>
      'Herramientas cargadas. Continúa esta conversación para usarlas.';

  @override
  String get connectorCardConnectedUnconfirmed =>
      'Conectado. La disponibilidad de herramientas no se ha confirmado.';

  @override
  String get connectorCardFailureUnavailable =>
      'Este agente no puede conectar herramientas desde una conversación.';

  @override
  String get connectorCardFailureInvalidSuggestion =>
      'Esta sugerencia de conector no está disponible. Explora conectores en Herramientas.';

  @override
  String get connectorCardFailureSetupRequired =>
      'Este conector necesita configuración en Herramientas antes de poder conectarse.';

  @override
  String get connectorCardFailureNameConflict =>
      'Ya existe un conector con este nombre. Revísalo en Herramientas.';

  @override
  String get connectorCardFailureSourceChanged =>
      'La conexión de esta conversación cambió. Reabre la tarjeta del conector.';

  @override
  String get connectorCardFailureConnectFailed =>
      'No se pudo confirmar la conexión. Revisa su estado antes de reintentar.';

  @override
  String get connectorCardFailureAuthenticationFailed =>
      'No se pudo confirmar el inicio de sesión. Revisa su estado antes de reintentar.';

  @override
  String get connectorCardFailureOauthUnavailable =>
      'Este conector no puede iniciar sesión desde una conversación.';

  @override
  String get connectorCardFailureNotConnected =>
      'El conector aún no está conectado. Revisa su configuración en Herramientas.';

  @override
  String get connectorCardOpenTools => 'Abrir los conectores en Herramientas';

  @override
  String get connectorCardTryAgain => 'Reintentar';

  @override
  String get connectorCardReopen => 'Reabrir la tarjeta';

  @override
  String get chatUiToolSearchedConnectors => 'Buscó conectores';

  @override
  String get chatUiConnectorSearchEmpty =>
      'No se encontraron conectores en el catálogo cargado.';

  @override
  String get chatUiConnectorSearchNotLoaded =>
      'El catálogo no se pudo cargar ahora. Pídelo otra vez en un momento.';

  @override
  String get chatUiConnectorSearchOff => 'Catálogo apagado';

  @override
  String get chatUiConnectorSearchOffBody =>
      'El catálogo de conectores es una lista pública de conectores. Activarlo descarga esa lista y envía tus palabras de búsqueda al registro público; nada se conecta.';

  @override
  String get chatUiConnectorSearchTurnOn => 'Activar el catálogo de conectores';

  @override
  String get chatUiConnectorSearchTurnedOn =>
      'Catálogo encendido. Pide al agente buscar otra vez.';

  @override
  String get chatUiConnectorSearchTurnOnFailed =>
      'No se pudo activar el catálogo. Reintenta.';

  @override
  String get chatUiConnectorSearchUnavailable =>
      'La búsqueda de conectores no está disponible. Reintenta desde Herramientas.';

  @override
  String get chatUiConnectorSearchNotConnected => 'Sin conexión';

  @override
  String get kitMarkdownImage => 'Imagen';

  @override
  String get toolCardPlan => 'Plan';

  @override
  String get toolCardWorktreeSetup => 'Preparando una copia aparte';

  @override
  String get toolCardWhatItDid => 'Qué hizo';

  @override
  String get toolCardTechnicalDetails => 'Detalles técnicos';

  @override
  String toolCardPageStatus(String status) {
    return 'La página respondió $status';
  }

  @override
  String toolCardAsked(String prompt) {
    return 'Preguntó: $prompt';
  }

  @override
  String get chatUiAgentNote => 'Nota';

  @override
  String get chatUiAgentWarning => 'Advertencia';

  @override
  String get chatUiAgentProblem => 'Problema';

  @override
  String get chatUiCompactedAutomatically =>
      'Se compactó automáticamente para hacer espacio.';

  @override
  String get chatUiCompactedWhenAsked => 'Se compactó cuando lo pediste.';

  @override
  String chatUiCompactedFrom(String tokens) {
    return 'Tenía unos $tokens tokens.';
  }

  @override
  String get chatRequestTitleFetch => 'Abrir una página web';

  @override
  String get chatRequestTitleWebSearch => 'Buscar en la web';

  @override
  String get chatRequestTitleSearch => 'Buscar en el proyecto';

  @override
  String get chatRequestTitleTask => 'Iniciar un agente auxiliar';

  @override
  String get chatRequestTitleSkill => 'Usar una habilidad';

  @override
  String get chatRequestTitleMode => 'Cambiar el modo del agente';

  @override
  String get chatRequestDetailRunsIn => 'Se ejecuta en';

  @override
  String get chatRequestDetailAsks => 'Qué solicita';

  @override
  String get chatRequestDetailRange => 'Parte del archivo';

  @override
  String get chatRequestDetailHelper => 'Tipo de ayudante';

  @override
  String chatRequestReadFrom(String line) {
    return 'desde la línea $line';
  }

  @override
  String chatRequestReadLines(num count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas',
      one: '1 línea',
    );
    return '$_temp0';
  }

  @override
  String get toolCardWhatWasSent => 'Lo que se envió';

  @override
  String get toolCardWhatCameBack => 'Lo que volvió';

  @override
  String get kitRequestShowMore => 'Mostrar más';

  @override
  String get kitRequestShowLess => 'Mostrar menos';

  @override
  String paseoCheckReady(String agents) {
    return 'Listos en esa computadora: $agents.';
  }

  @override
  String get paseoCheckNoneReady =>
      'Aún no hay ningún agente listo en esa computadora.';

  @override
  String get serverRowDetailsVersion => 'Versión';

  @override
  String get capabilityDevServices => 'Servidores dev';

  @override
  String get capabilityDevServicesDetail =>
      'Inicia el servidor o la vista previa de un proyecto, y detenlo después';

  @override
  String get capabilityWebSearch => 'Búsqueda web';

  @override
  String get capabilityWebSearchDetail =>
      'Deja que el agente busque cosas en la web';

  @override
  String get capabilityDeleteMessage => 'Eliminar un mensaje';

  @override
  String get capabilityDeleteMessageDetail =>
      'Quita un mensaje de una conversación';

  @override
  String get capabilitySavedPermissions => 'Aprobaciones guardadas';

  @override
  String get capabilitySavedPermissionsDetail =>
      'Ve y quita las acciones que siempre permitiste';

  @override
  String get capabilityMcpAdd => 'Agregar servidores MCP';

  @override
  String get capabilityMcpAddDetail =>
      'Agrega un servidor MCP o cambia sus ajustes desde la app';

  @override
  String get capabilityApprovals => 'Preguntar antes de actuar';

  @override
  String get capabilityApprovalsDetail =>
      'Elige si el agente pide aprobación o actúa por su cuenta';

  @override
  String get capabilitySteer => 'Enviar mientras trabaja';

  @override
  String get capabilitySteerDetail =>
      'Agrega un mensaje mientras el agente está ocupado; lo lee durante el turno actual';

  @override
  String get serverSettingsAuthPasswordSaved =>
      'Contraseña guardada en este teléfono';

  @override
  String get serverSettingsAuthTokenSaved =>
      'Token de conexión guardado en este teléfono';

  @override
  String get serverSettingsAuthTokenMissing => 'Sin token de conexión guardado';

  @override
  String connectionPasswordRejectedFor(String server) {
    return 'La contraseña de $server cambió. Actualízala para reconectar.';
  }

  @override
  String connectionTokenRejectedFor(String server) {
    return '$server rechazó el token de conexión. Actualízalo para reconectar.';
  }

  @override
  String serverSettingsEditTitle(String server) {
    return 'Editar $server';
  }

  @override
  String get serverSettingsEditDetail =>
      'Cambia su nombre, dirección o inicio de sesión';

  @override
  String serverSettingsRemoveTitle(String server) {
    return 'Quitar $server';
  }

  @override
  String get serverSettingsRecheckAgents => 'Revisar si hay agentes otra vez';

  @override
  String get serverSettingsRecheckAgentsDetail =>
      'Úsalo después de iniciar sesión en un agente en esa computadora.';

  @override
  String get serverSettingsRecheckChecking => 'Revisando si hay agentes…';

  @override
  String get serverSettingsRecheckFailed =>
      'No se pudo revisar si hay agentes. Reintenta en un momento.';

  @override
  String integrationsMcpDetails(String name) {
    return 'Detalles de $name';
  }

  @override
  String get usageBusiestDays => 'Días más ocupados';

  @override
  String usageDaySteps(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pasos',
      one: '1 paso',
    );
    return '$_temp0';
  }

  @override
  String modelPickerAgentUses(String model) {
    return 'Usa $model';
  }

  @override
  String get sessionMenuArchive => 'Archivar conversación';

  @override
  String get sessionMenuArchiveHint =>
      'Lo mueve a Archivadas; el servidor lo conserva';

  @override
  String get sessionMenuDelete => 'Eliminar conversación';

  @override
  String get sessionMenuDeleteHint =>
      'Lo elimina con su historial para siempre';

  @override
  String sessionArchiveTitle(String title) {
    return '¿Archivar “$title”?';
  }

  @override
  String get sessionArchiveBody =>
      'Pasa a Archivadas, donde aún puedes leerla.';

  @override
  String sessionDeleteTitle(String title) {
    return '¿Eliminar “$title”?';
  }

  @override
  String get sessionDeleteBody =>
      'La conversación y su historial se eliminan para siempre. No se puede deshacer.';

  @override
  String monitorUnreachableTitle(String server) {
    return 'No se puede alcanzar a $server';
  }

  @override
  String get monitorUnreachableDetail =>
      'Sus preguntas aparecerán aquí cuando vuelva.';

  @override
  String monitorUnreachableCheck(String server) {
    return 'Revisar $server otra vez';
  }

  @override
  String agentsStateLimitReset(String time) {
    return 'Límite del plan alcanzado · se restablece $time';
  }

  @override
  String get exportMarkdownUnredacted =>
      'La transcripción no está ofuscada. Contiene exactamente lo que ves, incluidos los secretos o rutas privadas que haya en la conversación.';

  @override
  String teamUiGateAnswerConfirmCancelNamedBody(String task) {
    return '“$task” se detiene. Su trabajo abierto se queda como está.';
  }

  @override
  String teamProjectStopNamedBody(String project) {
    return '“$project” se detiene. Sus tareas en marcha se detienen y su trabajo e historial se quedan.';
  }

  @override
  String teamProjectTaskStopNamedBody(String task) {
    return '“$task” se detiene. Su conversación y sus cambios se quedan por revisar.';
  }

  @override
  String get teamAgentUnavailableState => 'No puede arrancar';

  @override
  String get teamAgentUnavailableModelWords =>
      'Su modelo no está configurado en la computadora';

  @override
  String get teamAgentUnavailableOtherWords =>
      'La computadora no puede correrlo ahora mismo';

  @override
  String teamAgentUnavailableTitle(String agent) {
    return '$agent no puede arrancar';
  }

  @override
  String teamAgentUnavailableBody(String reason) {
    return '$reason. Arreglalo en la computadora y revisa otra vez.';
  }

  @override
  String get teamAgentUnavailableHostSays => 'Lo que la computadora dice';

  @override
  String get teamControlForceStop => 'Forzar la detención';

  @override
  String get teamProjectControlSuspendWord => 'Pausar el proyecto';

  @override
  String get teamProjectControlResumeWord => 'Reanudar el proyecto';

  @override
  String get teamProjectControlRemoveWord => 'Eliminar el proyecto';

  @override
  String get teamJobTurnOnWord => 'Activar';

  @override
  String get teamJobTurnOffWord => 'Apagar';

  @override
  String get teamHomeProjectsTitle => 'Proyectos';

  @override
  String get teamRigStateActive => 'Activo';

  @override
  String get teamRigStatePaused => 'Pausado';

  @override
  String teamRigPausedTitle(String project) {
    return '$project está pausado';
  }

  @override
  String get teamRigPausedBody =>
      'No arranca trabajo nuevo en él hasta que lo reanudes.';

  @override
  String teamRigPause(String project) {
    return 'Pausar el proyecto $project';
  }

  @override
  String teamRigResume(String project) {
    return 'Reanudar el proyecto $project';
  }

  @override
  String teamRigPauseTitle(String project) {
    return '¿Pausar $project?';
  }

  @override
  String teamRigPauseBody(String project) {
    return 'No arranca trabajo nuevo en “$project” hasta que lo reanudes. Nada se elimina.';
  }

  @override
  String get teamRigPauseConfirm => 'Pausar el proyecto';

  @override
  String teamRigDelete(String project) {
    return 'Eliminar el proyecto $project';
  }

  @override
  String teamRigDeleteTitle(String project) {
    return '¿Eliminar $project?';
  }

  @override
  String teamRigDeleteBody(String project) {
    return '“$project” sale de este equipo. Su carpeta y archivos en la computadora no se tocan.';
  }

  @override
  String get teamRigDeleteConfirm => 'Eliminar el proyecto';

  @override
  String get teamRigDeleteLost => 'El equipo deja de trabajar en él';

  @override
  String get teamRigDeleteKeptFiles =>
      'Su carpeta y sus archivos en la computadora';

  @override
  String get teamRigDeleteKeptHistory => 'Sus tareas pasadas y su historial';

  @override
  String get teamRigFolderLabel => 'Carpeta en la computadora';

  @override
  String get teamRigNotFoundTitle => 'Este proyecto desapareció';

  @override
  String get teamRigNotFoundBody =>
      'La computadora ya no lo lista. Vuelve al equipo.';

  @override
  String teamProjectDelete(String project) {
    return 'Eliminar el proyecto $project';
  }

  @override
  String teamProjectDeleteTitle(String project) {
    return '¿Eliminar $project?';
  }

  @override
  String teamProjectDeleteBody(String project) {
    return '“$project” se elimina del AI Team de este teléfono, junto con la copia del equipo de su código. Esto no se puede deshacer.';
  }

  @override
  String get teamProjectDeleteConfirm => 'Eliminar el proyecto';

  @override
  String get teamProjectDeleteLostPlan =>
      'Su plan, tareas e historial se eliminan';

  @override
  String get teamProjectDeleteLostRunning => 'El trabajo en marcha se detiene';

  @override
  String get teamProjectDeleteKept =>
      'La carpeta desde la que lo agregaste se queda como está';

  @override
  String teamAgentForceStop(String agent) {
    return 'Forzar la detención de $agent';
  }

  @override
  String teamAgentForceStopTitle(String agent) {
    return '¿Forzar la detención de $agent?';
  }

  @override
  String teamAgentForceStopBody(String agent) {
    return '$agent termina al instante, sin terminar lo que hace. El trabajo que no haya guardado puede perderse.';
  }

  @override
  String teamAgentStopStuckTitle(String agent) {
    return '$agent no se detuvo';
  }

  @override
  String get teamAgentStopStuckBody =>
      'La detención normal no lo terminó. Puedes forzar que termine ahora.';

  @override
  String get teamJobsTitle => 'Trabajos programados';

  @override
  String get teamJobsWholeTeamTitle => 'Para todo el equipo';

  @override
  String get teamJobsEmptyTitle => 'Sin trabajos programados';

  @override
  String get teamJobsEmptyBody =>
      'La computadora no arranca nada por sí sola para este proyecto.';

  @override
  String get teamJobsFailedTitle =>
      'No se pudieron leer los trabajos programados';

  @override
  String get teamJobsFailedBody =>
      'Revisa la conexión con la computadora y reintenta.';

  @override
  String get teamJobsLoading => 'Leyendo los trabajos programados';

  @override
  String teamJobEveryDayAt(String time) {
    return 'Todos los días a las $time';
  }

  @override
  String teamJobEveryWeekdayAt(String time) {
    return 'Todos los días de semana a las $time';
  }

  @override
  String teamJobEveryDowAt(String day, String time) {
    return 'Cada $day a las $time';
  }

  @override
  String get teamJobEveryHour => 'Cada hora';

  @override
  String teamJobEveryMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Cada $count minutos',
      one: 'Cada minuto',
    );
    return '$_temp0';
  }

  @override
  String teamJobEveryHours(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Cada $count horas',
      one: 'Cada hora',
    );
    return '$_temp0';
  }

  @override
  String teamJobEverySeconds(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Cada $count segundos',
      one: 'Cada segundo',
    );
    return '$_temp0';
  }

  @override
  String get teamJobOnSchedule => 'En un horario';

  @override
  String get teamJobRepeats => 'Se repite solo';

  @override
  String get teamJobWhenEvent => 'Cuando algo ocurre';

  @override
  String get teamJobManual => 'Solo cuando se arranca a mano';

  @override
  String get teamJobWebhook => 'Cuando llega una petición web';

  @override
  String get teamJobCondition => 'Cuando su revisión pasa';

  @override
  String get teamJobOther => 'Arranca solo';

  @override
  String get agentFeaturesLabel => 'Ajustes del agente';

  @override
  String agentFeaturesSheetTitle(String agent) {
    return 'Ajustes de $agent';
  }

  @override
  String agentFeatureChipOff(String name) {
    return '$name, desactivado';
  }

  @override
  String agentFeatureChipOn(String name) {
    return '$name, activado';
  }

  @override
  String get agentFeatureFastChip => 'Rápido';

  @override
  String get agentFeaturesLoadFailed =>
      'No se pudieron cargar los ajustes de este agente.';

  @override
  String get agentFeatureFastTitle => 'Modo rápido';

  @override
  String get agentFeatureFastDetail =>
      'Respuestas más rápidas con los modelos compatibles. Cuesta más.';

  @override
  String get agentFeatureSaving => 'Guardando…';

  @override
  String agentFeatureChangeFailed(String name) {
    return 'No se pudo cambiar $name. Conserva su ajuste anterior.';
  }

  @override
  String get claudeImportTitle => 'Importar desde Claude Code';

  @override
  String claudeImportIntro(String project) {
    return 'Elige una conversación que empezaste en Claude Code en $project. Se abre aquí para que puedas continuar.';
  }

  @override
  String claudeImportRowDetail(String project, String when) {
    return '$project · $when';
  }

  @override
  String get claudeImportUntitled => 'Conversación sin título';

  @override
  String get claudeImportEmptyTitle => 'Nada por importar';

  @override
  String get claudeImportEmptyBody =>
      'Las conversaciones que empieces en Claude Code en este proyecto aparecen aquí.';

  @override
  String get claudeImportLoadFailedTitle => 'No se pudo revisar';

  @override
  String get claudeImportLoadFailedBody =>
      'La lista no cargó. Revisa la conexión y reintenta.';

  @override
  String claudeImportAlready(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count conversaciones más ya están aquí.',
      one: '1 conversación más ya está aquí.',
    );
    return '$_temp0';
  }

  @override
  String get claudeImportWorking => 'Importando…';

  @override
  String get claudeImportFailed =>
      'No se pudo importar esa conversación. Reintenta.';

  @override
  String hostUpdateRow(String computer) {
    return 'Actualizar los agentes en $computer';
  }

  @override
  String hostUpdateRowRunning(String version) {
    return 'Corre $version. Instala el software de agentes más nuevo.';
  }

  @override
  String get hostUpdateRowUnknown =>
      'Instala el software de agentes más nuevo en esa computadora.';

  @override
  String get hostUpdateConfirmTitle => '¿Actualizar los agentes?';

  @override
  String hostUpdateConfirmBody(String computer) {
    return 'Esto instala el software de agentes más nuevo en $computer y lo reinicia.';
  }

  @override
  String hostUpdateConfirmPauses(String computer) {
    return 'Las conversaciones en $computer se pausan mientras se reinicia.';
  }

  @override
  String get hostUpdateConfirmKept =>
      'Tus conversaciones y ajustes se quedan como están.';

  @override
  String get hostUpdateConfirmAction => 'Actualizar los agentes';

  @override
  String get hostUpdateStarting => 'Iniciando la actualización…';

  @override
  String get hostUpdateDownloading => 'Descargando la versión más nueva…';

  @override
  String get hostUpdateInstalling => 'Instalando…';

  @override
  String get hostUpdateFinishing => 'Terminando…';

  @override
  String hostUpdateDone(String version) {
    return 'Actualizado a $version. La app se reconecta sola.';
  }

  @override
  String get hostUpdateDoneNoVersion =>
      'Actualizado. La app se reconecta sola.';

  @override
  String hostUpdateCurrent(String computer) {
    return '$computer ya está al día.';
  }

  @override
  String get hostUpdateFailed =>
      'La actualización no terminó. Reintenta, o actualízala en esa computadora.';

  @override
  String get hostUpdateDropped =>
      'La conexión se cortó durante la actualización. La app se está reconectando.';

  @override
  String get hostUpdateDetailsRow => 'Por qué falló';

  @override
  String get hostUpdateDetailsTitle => 'Detalles de la actualización';

  @override
  String get hostUpdateDetailsMessage => 'Mensaje de la computadora';

  @override
  String get filesTextSurface => 'Texto en archivos';

  @override
  String get filesSearchText => 'Buscar texto en archivos';

  @override
  String filesSearchInside(String query) {
    return 'Buscar \"$query\" dentro de los archivos';
  }

  @override
  String get filesTextHintTitle => 'Buscar dentro de los archivos';

  @override
  String get filesTextHintBody =>
      'Escribe una palabra o una frase para ver todas las líneas que la contienen.';

  @override
  String get filesNoTextTitle => 'Sin coincidencias';

  @override
  String get filesNoTextBody =>
      'Ningún archivo tiene ese texto. Revisa la ortografía o prueba con menos palabras.';

  @override
  String get filesTextFailedTitle => 'No se pudo buscar en los archivos';

  @override
  String filesTextLimited(int count) {
    return 'Mostrando las primeras $count coincidencias. Escribe más para acotarlas.';
  }

  @override
  String filesTextMatchLine(int line) {
    return 'Línea $line';
  }

  @override
  String get productErrorComputer =>
      'No se pudo contactar con tu computadora. Comprueba que esté encendida y conectada, e inténtalo de nuevo.';

  @override
  String get productErrorProjectGit =>
      'El historial de Git de este proyecto no se puede leer, así que la conversación no pudo empezar. Restaura el proyecto desde una copia, o clónalo otra vez.';

  @override
  String kitGalleryPosition(int current, int total) {
    return '$current de $total';
  }

  @override
  String kitGalleryPictureLabel(int current, int total, String name) {
    return 'Imagen $current de $total: $name';
  }

  @override
  String get kitGalleryEdit => 'Editar';

  @override
  String get kitGallerySend => 'Enviar';

  @override
  String get kitGalleryLoading => 'Cargando la imagen';

  @override
  String get kitGalleryLoadFailed => 'No se pudo cargar esta imagen';

  @override
  String get kitGalleryLoadFailedBody => 'Revisa la conexión y reintenta.';

  @override
  String get kitGalleryTryAgain => 'Reintentar';

  @override
  String get kitMarkupTitle => 'Editar la imagen';

  @override
  String get kitMarkupColours => 'Color del lápiz';

  @override
  String get kitMarkupRed => 'Rojo';

  @override
  String get kitMarkupYellow => 'Amarillo';

  @override
  String get kitMarkupWhite => 'Blanco';

  @override
  String get kitMarkupBlack => 'Negro';

  @override
  String get kitMarkupUndo => 'Deshacer';

  @override
  String get kitMarkupClear => 'Borrar';

  @override
  String get kitMarkupCrop => 'Recortar';

  @override
  String get kitMarkupApply => 'Aplicar';

  @override
  String get kitMarkupCancel => 'Cancelar';

  @override
  String get kitMarkupDone => 'Listo';

  @override
  String get kitMarkupCanvas =>
      'Imagen en edición. Dibuja sobre ella con el dedo.';

  @override
  String get kitMarkupCropHandle =>
      'Esquina del recorte. Arrastra para cambiar el tamaño.';

  @override
  String get kitMarkupCropArea => 'Área de recorte. Arrastra para moverla.';

  @override
  String get kitMarkupFailed => 'No se pudo hacer la imagen editada';

  @override
  String get kitMarkupFailedBody =>
      'Reintenta, o vuelve atrás y envía el original.';

  @override
  String kitContextRingSemantics(String percent) {
    return 'Contexto $percent % usado';
  }

  @override
  String get kitContextRingTapHint => 'Mostrar los detalles del contexto';

  @override
  String get kitContextRingCompacting => 'Compactando';

  @override
  String get kitContextRingCompactingSemantics => 'Contexto, compactando';

  @override
  String get kitContextSheetTitle => 'Ventana de contexto';

  @override
  String kitContextSheetTokens(String used, String limit) {
    return '$used de $limit tokens';
  }

  @override
  String get kitContextSheetModel => 'Modelo';

  @override
  String get kitContextSheetWindow => 'Ventana de contexto';

  @override
  String get kitContextSheetMeasured => 'Medido';

  @override
  String get kitContextSheetMeasuredValue => 'Después de la última respuesta';

  @override
  String get kitContextSheetExplain =>
      'La ventana de contexto es cuánta conversación puede leer el modelo de una vez: tus mensajes, sus respuestas, archivos y resultados de herramientas. Cuando se llena, las partes viejas tienen que resumirse o quedarse fuera, así que compactarla antes conserva las respuestas nítidas.';

  @override
  String get kitContextSheetChartTitle => 'Cada respuesta';

  @override
  String kitContextChartSemantics(String count, String percent) {
    return 'Contexto después de cada una de $count respuestas, la más nueva en $percent %';
  }

  @override
  String get kitContextChartCompacted => 'Compactado aquí';

  @override
  String get kitContextSheetCompactNow => 'Compactar ahora';

  @override
  String get phraseChipsRowLabel => 'Frases rápidas';

  @override
  String phraseChipsSend(String phrase) {
    return 'Enviar: $phrase';
  }

  @override
  String get phraseChipsMenuLabel => 'Opciones de frases';

  @override
  String get phraseChipsEdit => 'Editar';

  @override
  String get phraseChipsHide => 'Ocultar';

  @override
  String get settingsPhraseChipsTitle => 'Sugerencias de frases';

  @override
  String get settingsPhraseChipsSupporting =>
      'Tus frases cortas comunes, sobre la caja de mensajes. Aprendidas en este teléfono de lo que envías.';

  @override
  String get settingsPhraseChipsShowHidden => 'Mostrar frases ocultas';

  @override
  String settingsPhraseChipsHiddenCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count frases ocultas',
      one: '1 frase oculta',
    );
    return '$_temp0';
  }

  @override
  String get kitFactToldWord => 'Tú se lo dijiste';

  @override
  String get kitFactSeenWord => 'De una fuente';

  @override
  String get kitFactGuessedWord => 'Lo adivinó';

  @override
  String get kitFactCalculatedWord => 'Lo calculó';

  @override
  String kitFactToldLine(String date) {
    return 'Tú se lo dijiste · $date';
  }

  @override
  String kitFactSeenLine(String source, String date) {
    return 'De $source · $date';
  }

  @override
  String kitFactSeenNoSourceLine(String date) {
    return 'De una fuente · $date';
  }

  @override
  String kitFactGuessedLine(String date) {
    return 'Lo adivinó · $date';
  }

  @override
  String kitFactGuessedFromLine(String date, String source) {
    return 'Lo adivinó · $date · de $source';
  }

  @override
  String kitFactCalculatedLine(String date) {
    return 'Lo calculó · $date';
  }

  @override
  String kitFactWaitingLine(String line) {
    return '$line · esperando tu visto bueno';
  }

  @override
  String get fbSourceChat => 'una conversación que guardaste';

  @override
  String get fbSourceShare => 'texto que compartiste';

  @override
  String get fbSourceNote => 'una nota';

  @override
  String get fbSourceImport => 'texto pegado';

  @override
  String get fbSourceStatement => 'una afirmación';

  @override
  String get fbSourceOther => 'otra fuente';

  @override
  String get fbKindPerson => 'Persona';

  @override
  String get fbKindDocument => 'Documento';

  @override
  String get fbKindSubscription => 'Suscripción';

  @override
  String get fbKindWarranty => 'Garantía';

  @override
  String get fbKindBill => 'Cuenta';

  @override
  String get fbKindCard => 'Tarjeta';

  @override
  String get fbKindAccount => 'Cuenta';

  @override
  String get fbKindOrganization => 'Organización';

  @override
  String get fbKindPlace => 'Lugar';

  @override
  String get fbKindProject => 'Proyecto';

  @override
  String get fbKindTopic => 'Tema';

  @override
  String get fbKindEvent => 'Evento';

  @override
  String get fbKindOther => 'Cosa';

  @override
  String get fbMemoryTitle => 'Memoria';

  @override
  String fbMemoryFromFactsBook(int count) {
    return '$count esperando';
  }

  @override
  String get fbMemoryFromFactsBookNone => 'Todo en claro';

  @override
  String fbMemoryReview(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count cambios por revisar',
      one: '1 cambio por revisar',
    );
    return '$_temp0';
  }

  @override
  String fbMemoryNotSaved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count notas aún sin guardar',
      one: '1 nota aún sin guardar',
    );
    return '$_temp0';
  }

  @override
  String get fbMemoryNotSavedDetail => 'Se conserva en este teléfono';

  @override
  String get fbMemoryReviewDetail => 'Necesita tu visto bueno';

  @override
  String get fbMemoryRecent => 'Recién agregado a la memoria';

  @override
  String get fbMemoryOpenAboutYou => 'Abrir Acerca de ti';

  @override
  String get fbMemoryOpenAboutYouDetail => 'Busca, explora y ve lo que espera';

  @override
  String get fbMemoryAddSomething => 'Agregar algo';

  @override
  String get fbMemoryAddSomethingDetail => 'Una nota o texto pegado';

  @override
  String get fbAboutYouTitle => 'Acerca de ti';

  @override
  String get fbStatusStale => 'Mostrando lo que se guardó la última vez';

  @override
  String get fbSearchLabel => 'Buscar en tu memoria';

  @override
  String get fbEmptyTitle => 'Aún nada aquí';

  @override
  String get fbEmptyBody =>
      'Agrega una nota, comparte texto desde otra app, o pega texto de otro asistente. Lo que agregues aparece aquí con su procedencia.';

  @override
  String get fbListAll => 'Todo';

  @override
  String get fbListCards => 'Tarjetas';

  @override
  String get fbListPeople => 'Personas';

  @override
  String get fbListDocuments => 'Documentos';

  @override
  String get fbListLockedNote =>
      'Las notas bloqueadas no se muestran. Desbloquéalas con tu huella o PIN para incluirlas.';

  @override
  String fbKindLine(String kind, String detail) {
    return '$kind · $detail';
  }

  @override
  String get fbAreaMoney => 'Área de dinero';

  @override
  String get fbAreaHealth => 'Área de salud';

  @override
  String fbAreaOther(String area) {
    return 'Área de $area';
  }

  @override
  String get fbErrorTitle => 'La memoria no respondió';

  @override
  String get fbErrorUnreachable =>
      'No se pudo alcanzar la memoria. Lo que agregues se conserva en este teléfono hasta que pueda guardarse.';

  @override
  String get fbErrorLocked =>
      'Esto está en la carpeta bloqueada. Desbloquéala primero.';

  @override
  String get fbErrorNotFound => 'Eso ya no está en la memoria.';

  @override
  String get fbErrorGeneric => 'Algo salió mal. Reintenta.';

  @override
  String fbErrorRequest(String id) {
    return 'Petición $id';
  }

  @override
  String get fbSearchTitle => 'Buscar';

  @override
  String fbSearchMatches(int count, String query) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count coincidencias con “$query”',
      one: '1 coincidencia con “$query”',
      zero: 'Sin coincidencias con “$query”',
    );
    return '$_temp0';
  }

  @override
  String get fbSearchWordsOnly =>
      'Coincide con palabras de tus notas. No busca por significado.';

  @override
  String get fbSearchEachHit =>
      'Cada resultado muestra de dónde vino y cuándo. Una fuente igual puede estar equivocada.';

  @override
  String get fbSearchLocked =>
      'Las notas bloqueadas no se buscan hasta que las desbloquees.';

  @override
  String get fbSearchNoneTitle => 'Nada coincide';

  @override
  String get fbSearchNoneBody => 'Prueba otras palabras.';

  @override
  String get fbThingFacts => 'Datos';

  @override
  String get fbThingNoFacts => 'Aún sin datos.';

  @override
  String get fbThingLocked => 'Carpeta bloqueada';

  @override
  String get fbCaptureTitle => 'Agregar algo';

  @override
  String get fbCaptureKindLabel => 'Lo que estás agregando';

  @override
  String get fbCaptureNote => 'Nota';

  @override
  String get fbCapturePaste => 'Pegar';

  @override
  String get fbCaptureNoteLabel => '¿Qué debe recordar?';

  @override
  String get fbCaptureNoteHint =>
      'Dentista el próximo miércoles a las 10. Llevar la tarjeta del seguro.';

  @override
  String get fbCapturePasteLabel => 'Pega texto de otra app o asistente';

  @override
  String get fbCaptureSave => 'Guardar';

  @override
  String get fbCaptureFootnote =>
      'Lo compartido desde otra app también llega aquí. Una nota que no pueda guardarse todavía espera en Aún sin guardar. Nunca desaparece.';

  @override
  String get fbCaptureSaved =>
      'Guardado. La memoria le revisará los cambios para que los apruebes.';

  @override
  String fbCaptureSavedReminder(String when) {
    return 'Guardado con una alarma para $when.';
  }

  @override
  String get fbCaptureKept =>
      'Aún no se pudo guardar. Se conserva en este teléfono.';

  @override
  String get fbCaptureSeeNotSaved => 'Ver Aún sin guardar';

  @override
  String get fbReminderTitle => 'Revisar esta alarma';

  @override
  String fbReminderWrote(String text) {
    return 'Escribiste: “$text”';
  }

  @override
  String get fbReminderDate => 'Fecha';

  @override
  String get fbReminderTime => 'Hora';

  @override
  String get fbReminderZone => 'Zona horaria';

  @override
  String get fbReminderZoneHome => 'Zona horaria de casa';

  @override
  String get fbReminderZoneHomeDetail =>
      'Suena a esta hora en tu zona de casa, dondequiera que estés.';

  @override
  String get fbReminderZoneFloating => 'La hora de este teléfono';

  @override
  String get fbReminderZoneFloatingDetail =>
      'Se queda a la misma hora de reloj dondequiera que estés.';

  @override
  String get fbReminderLocale => 'Idioma de las fechas';

  @override
  String get fbReminderRepeat => 'Se repite';

  @override
  String get fbRepeatNever => 'Nunca';

  @override
  String get fbRepeatDaily => 'Cada día';

  @override
  String get fbRepeatWeekly => 'Cada semana';

  @override
  String get fbRepeatMonthly => 'Cada mes';

  @override
  String get fbRepeatYearly => 'Cada año';

  @override
  String get fbReminderAmbiguousDate => 'No pude saber la fecha. Revísala.';

  @override
  String get fbReminderAmbiguousTime => 'No pude saber la hora. Revísala.';

  @override
  String get fbReminderHelp => 'Cambia lo que esté mal antes de guardar.';

  @override
  String get fbReminderSave => 'Guardar la alarma';

  @override
  String get fbReminderChange => 'Cambia lo que escribí';

  @override
  String get fbReminderWhat => 'Recordarme sobre';

  @override
  String get fbNeedsYouTitle => 'Te espera';

  @override
  String fbNeedsYouWaiting(int count) {
    return '$count esperando';
  }

  @override
  String get fbNeedsYouNone => 'Nada te necesita ahora mismo.';

  @override
  String get fbNeedsYouNoneBody =>
      'Las preguntas, los cambios por revisar y las notas aún sin guardar aparecen aquí.';

  @override
  String get fbNotSavedTitle => 'Aún sin guardar';

  @override
  String fbNotSavedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count elementos conservados en este teléfono',
      one: '1 elemento conservado en este teléfono',
    );
    return '$_temp0';
  }

  @override
  String get fbNotSavedIntro =>
      'Se conservan hasta que los descartes: en este teléfono, o por la Memoria. Ninguno está aún en tu nube.';

  @override
  String get fbNotSavedFooter =>
      'Se quedan aquí hasta que los descartes. A los 30 días la Memoria pregunta: Reintentar, Exportar o Descartar.';

  @override
  String get fbNotSavedEmptyTitle => 'Todo está guardado';

  @override
  String get fbNotSavedEmptyBody =>
      'Las notas que no puedan guardarse todavía esperarán aquí.';

  @override
  String get fbNotSavedTryNow => 'Probar ahora';

  @override
  String get fbNotSavedCopy => 'Copiar el texto';

  @override
  String get fbNotSavedDiscard => 'Descartar la nota';

  @override
  String get fbNotSavedRetry => 'Reintentar';

  @override
  String get fbNotSavedExport => 'Exportar';

  @override
  String get fbNotSavedThirtyDays =>
      'Esto se ha conservado 30 días. Reinténtalo, exporta el texto o descártalo.';

  @override
  String get fbNotSavedDiscardTitle => '¿Descartar esta nota?';

  @override
  String get fbNotSavedDiscardBody =>
      'Nunca se guardó. Descartarla la quita de este teléfono.';

  @override
  String get fbNotSavedSaved => 'Guardado.';

  @override
  String get fbNotSavedStill => 'Aún sin guardar. Se queda aquí.';

  @override
  String fbNotSavedSince(String when) {
    return 'Desde $when';
  }

  @override
  String get fbReasonUnreachable => 'No se pudo alcanzar la memoria.';

  @override
  String get fbReasonStorageFull =>
      'Este teléfono está lleno. Libera algo de espacio y reintenta.';

  @override
  String get fbReasonCloudQuota =>
      'Tu nube está llena. Libera algo de espacio y reintenta.';

  @override
  String get fbReasonKeyLocked =>
      'La carpeta bloqueada está bloqueada. Desbloquéalala y reintenta.';

  @override
  String get fbReasonRejected => 'La memoria no pudo aceptarlo.';

  @override
  String get fbReasonClassification =>
      'La memoria aún no pudo clasificarlo. Reintenta.';

  @override
  String get fbDismiss => 'Ocultar';

  @override
  String get fbCaptureNeedsText => 'Escribe algo primero.';

  @override
  String get fbAreaFamily => 'Área de familia';

  @override
  String get fbAreaWork => 'Área de trabajo';

  @override
  String get fbAreaNameMoney => 'Dinero';

  @override
  String get fbAreaNameHealth => 'Salud';

  @override
  String get fbAreaNameFamily => 'Familia';

  @override
  String get fbAreaNameWork => 'Trabajo';

  @override
  String get fbAreasTitle => 'Áreas y compartición';

  @override
  String get fbAreasIntro =>
      'Cada área guarda sus propias reglas. Otras apps y agentes leen un área solo si se lo permites.';

  @override
  String get fbAreasOthersRead => 'Otras apps y agentes pueden leerla';

  @override
  String get fbAreasOthersReadDetail =>
      'Apágalo para conservar esta área solo para ti.';

  @override
  String get fbAreasLockedFolder => 'Carpeta bloqueada';

  @override
  String get fbAreasNormalStorage => 'Almacenamiento normal';

  @override
  String get fbAreasReviewFirst =>
      'Los datos nuevos esperan tu visto bueno primero.';

  @override
  String get fbAreasReviewAuto => 'Solo la limpieza segura ocurre sola.';

  @override
  String get fbAreasFixed => 'Estas áreas vienen de fábrica.';

  @override
  String fbAreasShareConfirmTitle(String area) {
    return '¿Permitir que otros lean $area?';
  }

  @override
  String get fbAreasShareConfirmBody =>
      'Las apps y agentes que hayas emparejado podrán leer lo que está en esta área. Puedes apagarlo otra vez.';

  @override
  String get fbAreasShareConfirm => 'Permitir que la lean';

  @override
  String get fbCaptureFull =>
      'La fila de este teléfono está llena, así que la nota no se conservó. Tu texto sigue aquí. Envía o descarta una nota en espera primero.';

  @override
  String get fbCaptureQueuedNode =>
      'La memoria lo retiene hasta que pueda guardarse.';

  @override
  String get fbCaptureReminderFailed =>
      'La alarma no se hizo. Reinténtala desde la nota.';

  @override
  String fbCareWaiting(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count esperando tu visto bueno',
      one: '1 esperando tu visto bueno',
    );
    return '$_temp0';
  }

  @override
  String get fbCareApprove => 'Aprobar';

  @override
  String get fbCareReject => 'Rechazar';

  @override
  String get fbCareApproved => 'Aprobado.';

  @override
  String get fbCareRejected => 'Rechazado. No se propondrá otra vez.';

  @override
  String get fbCareSkipped => 'Pasado. Se queda aquí.';

  @override
  String get fbCareStale =>
      'Eso cambió después de proponerse, así que no se aplicó nada. Ya no está en esta lista.';

  @override
  String get fbCareNeedsOwner =>
      'Este teléfono aún no puede aprobar cambios. Configura la aprobación para continuar.';

  @override
  String get fbCareNeedsOwnerShort => 'Configurar primero la aprobación';

  @override
  String get fbCareSetupApproval => 'Configurar la aprobación';

  @override
  String get fbCareEditTitle => 'Editar antes de aprobar';

  @override
  String get fbCareEditLabel => 'Dato';

  @override
  String get fbCareEditSave => 'Aprobar con mi edición';

  @override
  String get fbCareClassFactAdd => 'Agregar un dato';

  @override
  String get fbCareClassFactUpdate => 'Cambiar un dato';

  @override
  String get fbCareClassFactSupersede => 'Reemplazar un dato';

  @override
  String get fbCareClassMerge => 'Fusionar dos cosas';

  @override
  String get fbCareClassLink => 'Vincular dos cosas';

  @override
  String get fbCareClassArchive => 'Archivar';

  @override
  String get fbCareClassForget => 'Olvidar';

  @override
  String get fbCareClassWatch => 'Vigilar algo';

  @override
  String get fbCareClassReminder => 'Alarma';

  @override
  String get fbCareClassImport => 'De texto pegado';

  @override
  String get fbCareClassPromotion => 'Un dato para un área más estricta';

  @override
  String get fbCareRiskMoney => 'Dinero';

  @override
  String get fbCareRiskHealth => 'Salud';

  @override
  String get fbCareRiskId => 'ID';

  @override
  String get fbCareRiskContradiction => 'Contradice algo que le dijiste';

  @override
  String get fbCareRiskLowConfidence => 'Sin certeza';

  @override
  String get fbConnectTitle => 'Conectar a mi servidor';

  @override
  String get fbConnectRow => 'Configurar la Memoria';

  @override
  String get fbConnectRowDetail => 'Tus notas, en este teléfono';

  @override
  String get fbConnectIntro =>
      'Conecta esta app a un servidor de Memory en tu red de casa, tu tailnet o en este teléfono.';

  @override
  String get fbConnectAddress => 'Dirección del nodo';

  @override
  String get fbConnectAddressHint => '127.0.0.1:8787';

  @override
  String get fbConnectCode => 'Código de emparejamiento de un solo uso';

  @override
  String get fbConnectCodeHelp =>
      'El nodo lo imprime cuando corres su comando de emparejamiento. Funciona una vez, por cinco minutos.';

  @override
  String get fbConnectOwnerTitle => 'Aprobar cambios (opcional)';

  @override
  String get fbConnectOwnerBody =>
      'Revisar propuestas y cambiar la compartición se firma con la clave propia de este teléfono. Dale al nodo la clave pública de abajo y luego escribe el ID de dispositivo dueño que devuelva.';

  @override
  String get fbConnectOwnerKey => 'La clave pública de este teléfono';

  @override
  String get fbConnectOwnerId => 'ID de dispositivo dueño';

  @override
  String get fbConnectButton => 'Conectar';

  @override
  String get fbConnectNeeds => 'Escribe primero la dirección y el código.';

  @override
  String get fbConnectFailedTitle => 'No se pudo conectar';

  @override
  String fbDisconnect(String name) {
    return 'Desconectar $name';
  }

  @override
  String get fbDisconnectDetail =>
      'Olvida el emparejamiento, el token y la clave de aprobación en este teléfono';

  @override
  String fbDisconnectTitle(String name) {
    return '¿Desconectar $name?';
  }

  @override
  String get fbDisconnectBody =>
      'Este teléfono olvida el emparejamiento, el token y la clave de aprobación del nodo. Las notas del nodo se quedan donde están, y las notas en espera en este teléfono se quedan hasta que las descartes.';

  @override
  String get fbErrorAddress =>
      'Esa dirección no está en una red privada. Usa este teléfono, tu red de casa o tu tailnet.';

  @override
  String get fbErrorBadResponse =>
      'La Memoria respondió de una manera que esta app no puede leer. Actualiza la app, o actualiza el programa de memoria.';

  @override
  String get fbErrorForbidden => 'Este teléfono no tiene permitido hacer eso.';

  @override
  String get fbErrorNotEnabled => 'Eso está apagado en esta memoria.';

  @override
  String get fbErrorOwner =>
      'Este teléfono aún no puede aprobar cambios. Configura primero la aprobación.';

  @override
  String get fbErrorPairCode =>
      'Ese código no funcionó. Pide al nodo uno nuevo.';

  @override
  String get fbErrorSlow => 'La memoria está ocupada. Reintenta en un momento.';

  @override
  String get fbErrorStale =>
      'Eso cambió desde que lo miraste. Míralo otra vez y reintenta una vez más.';

  @override
  String get fbErrorStarting =>
      'La memoria está arrancando. Reintenta en un momento.';

  @override
  String get fbErrorUnauthorized =>
      'Este teléfono no ha iniciado sesión en Memory. Configúralo otra vez.';

  @override
  String fbErrorCode(String code) {
    return 'Código $code';
  }

  @override
  String get fbKeyLocked => 'Bloqueada';

  @override
  String get fbKeyUnlocked => 'Desbloqueada';

  @override
  String get fbKindInvoice => 'Factura';

  @override
  String get fbKindHealth => 'Salud';

  @override
  String get fbModuleStorage => 'Almacenamiento';

  @override
  String get fbModuleCloud => 'Respaldo en la nube';

  @override
  String get fbModuleCare => 'Te espera';

  @override
  String get fbNotSavedAtNode => 'Retenida por la Memoria';

  @override
  String get fbNotSavedOnPhone => 'En este teléfono';

  @override
  String get fbNotSavedNodeItem => 'Una nota que la Memoria retiene';

  @override
  String get fbReasonModuleNotConfigured => 'nada conectado aún';

  @override
  String get fbReasonModuleNotEnabled => 'aún sin activar';

  @override
  String get fbReasonOutboxFull =>
      'La fila de este teléfono está llena. Envía o descarta una nota en espera.';

  @override
  String get fbReasonParserError => 'La memoria no pudo leerlo. Reintenta.';

  @override
  String get fbReminderMismatch =>
      'La memoria lo leyó de otra manera. Revisa la fecha, la hora y la zona y reintenta.';

  @override
  String fbReminderPause(String what) {
    return 'Pausar “$what”';
  }

  @override
  String fbReminderResume(String what) {
    return 'Reanudar “$what”';
  }

  @override
  String get fbRemindersTitle => 'Alarmas';

  @override
  String get fbRemindersActive => 'Activas';

  @override
  String get fbRemindersPaused => 'Pausadas';

  @override
  String get fbRemindersDone => 'Hechas';

  @override
  String get fbRemindersEmptyTitle => 'Aún no hay alarmas';

  @override
  String get fbRemindersEmptyBody =>
      'Agrega una alarma, o activa “Recordármelo también” al agregar una nota, y aparecerá aquí.';

  @override
  String get fbSourceSaved => 'algo que guardaste';

  @override
  String get fbStateReady => 'Lista';

  @override
  String get fbStateWaiting => 'Esperando';

  @override
  String get fbStateStale => 'Desactualizada';

  @override
  String get fbStateFailed => 'Falló';

  @override
  String get fbStateOff => 'Apagada';

  @override
  String get fbStateNotSetUp => 'Sin configurar';

  @override
  String get fbStatusTitle => 'Estado';

  @override
  String get fbStatusSearch => 'Búsqueda';

  @override
  String get fbStatusBackup => 'Respaldo';

  @override
  String get fbStatusBackedUp => 'Con respaldo';

  @override
  String get fbStatusNoBackup => 'Aún sin respaldo';

  @override
  String get fbStatusThisPhone => 'Este teléfono';

  @override
  String get fbStatusApprove => 'Aprobar los cambios desde este teléfono';

  @override
  String fbStatusSince(String when) {
    return 'actualizado hace $when';
  }

  @override
  String get fbThingWaitingNote =>
      'Los datos marcados “esperando tu visto bueno” están en Te espera.';

  @override
  String fbWhenDaily(String time) {
    return 'Cada día a las $time';
  }

  @override
  String get fbWhenCustom => 'Horario personalizado';

  @override
  String get fbDeveloperFactsBook => 'Memoria (vista previa)';

  @override
  String get fbDeveloperFactsBookDetail =>
      'Muestra la sección Memoria en tus conversaciones. Apagada por defecto.';

  @override
  String get fbDeveloperTitle => 'Desarrollador';

  @override
  String get fbErrorBadRequest =>
      'La memoria no aceptó eso. Revisa lo que escribiste y reintenta.';

  @override
  String get fbErrorNodeFull =>
      'La fila propia de la memoria está llena. Conserva tu texto y reintenta más tarde.';

  @override
  String get fbErrorIndexRebuilding =>
      'La búsqueda se está preparando. Reintenta en un momento.';

  @override
  String get fbErrorAuthUnavailable =>
      'Este teléfono no tiene huella, rostro ni bloqueo de pantalla configurados, así que no puede aprobar cambios. Configura uno en los ajustes del teléfono y reintenta.';

  @override
  String get fbErrorAuthCancelled =>
      'La aprobación se canceló. No se cambió nada.';

  @override
  String get fbErrorAuthLocked =>
      'Demasiados intentos errados. Espera un poco y reintenta.';

  @override
  String get fbErrorAuthFailed =>
      'El teléfono no pudo confirmar que eres tú. No se cambió nada.';

  @override
  String fbErrorSlowAfter(int seconds) {
    return 'La memoria está ocupada. Reintenta en $seconds segundos.';
  }

  @override
  String get fbAuthReason => 'Aprueba este cambio en la Memoria';

  @override
  String fbWhenWeeklyOn(String days, String time) {
    return 'Cada semana los $days a las $time';
  }

  @override
  String fbWhenMonthLast(String time) {
    return 'Cada mes el último día a las $time';
  }

  @override
  String fbWhenMonthlyOn(int day, String time) {
    return 'Cada mes el día $day a las $time';
  }

  @override
  String fbWhenYearlyOn(String date, String time) {
    return 'Cada año el $date a las $time';
  }

  @override
  String get fbReminderNeedsTitle => 'Escribe primero la alarma.';

  @override
  String get fbReminderWhatHint => 'Llamar al dentista';

  @override
  String get fbReminderDayOfMonth => 'Día del mes';

  @override
  String get fbReminderLastDay => 'Último día';

  @override
  String get fbRemindersAdd => 'Agregar una alarma';

  @override
  String fbThingOriginally(String source, String date) {
    return 'Leído originalmente de $source · $date';
  }

  @override
  String get fbExportTitle => 'Exportar esta nota';

  @override
  String get fbExportBody =>
      'Esta es la nota como objeto cifrado. No desbloquea la carpeta bloqueada, y abrirla necesita los datos de recuperación de cuando se configuró Memory en su propio dispositivo.';

  @override
  String get fbExportObject => 'Objeto cifrado';

  @override
  String get fbExportCopy => 'Copiar la exportación';

  @override
  String get fbSettingsTitle => 'Ajustes de la memoria';

  @override
  String get fbSettingsBrief => 'Boletín matutino';

  @override
  String get fbSettingsBriefCap => 'Elementos por día';

  @override
  String get fbSettingsBriefCapDetail =>
      'El máximo que muestra el boletín en un día.';

  @override
  String get fbSettingsBriefOff => 'Ninguno';

  @override
  String fbSettingsBriefItems(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count elementos',
      one: '1 elemento',
    );
    return '$_temp0';
  }

  @override
  String get fbSettingsBriefTime => 'Hora del boletín';

  @override
  String get fbSettingsQuietHours => 'Horas tranquilas';

  @override
  String get fbSettingsPrivacy => 'Privacidad';

  @override
  String get fbSettingsLockTimeout => 'La carpeta bloqueada queda abierta por';

  @override
  String fbSettingsMinutes(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count minutos',
      one: '1 minuto',
    );
    return '$_temp0';
  }

  @override
  String get fbSettingsAreasDetail => 'Quién más puede leer cada área';

  @override
  String get fbSettingsThisNode => 'Esta memoria';

  @override
  String get fbSettingsHomeZone => 'Zona horaria de casa';

  @override
  String get fbSettingsSpending => 'Límite de gasto';

  @override
  String get fbSettingsSpendingNone => 'No se puede gastar nada';

  @override
  String get fbSettingsFootnote =>
      'La duración del bloqueo y la zona de casa se fijan en el servidor de memoria. Los cambios aquí necesitan la aprobación de este teléfono.';

  @override
  String get fbSettingsNeedsOwner =>
      'Este teléfono aún no puede aprobar cambios, así que los ajustes se muestran pero no se cambian. Configura la aprobación para cambiarlos.';

  @override
  String get fbModuleReplication => 'Replicación';

  @override
  String get fbStatusChecked => 'Revisado';

  @override
  String get fbStatusUnknown => 'No se sabe';

  @override
  String get fbStatusHistory => 'Historial con firmas';

  @override
  String get fbStatusHistoryNotChecked => 'Sin revisar';

  @override
  String get fbStatusHistoryVerified =>
      'Verificado contra la clave fijada al emparejar';

  @override
  String get fbStatusHistoryFailed =>
      'No se pudo verificar. No apruebes nada hasta que lo esté.';

  @override
  String get fbStatusHistoryForked =>
      'Se encontraron dos historiales en competencia. Las aprobaciones están pausadas.';

  @override
  String get fbStatusNoReplication =>
      'La replicación está apagada, así que tus notas existen solo en esta memoria, y no se puede saber cuán fresca es una copia en la nube.';

  @override
  String get fbStatusNotYet => 'Sin activar';

  @override
  String get fbNotYetCloud => 'Sincronización y respaldo en la nube';

  @override
  String get fbNotYetUnlock => 'Abrir la carpeta bloqueada';

  @override
  String get fbNotYetAlarms => 'Alarmas de las alarmas en este teléfono';

  @override
  String get fbNotAvailableYet => 'Apagado';

  @override
  String get fbReminderClockNote =>
      'Si una hora no existe ese día por el cambio de relojes, corre a la primera hora que exista.';

  @override
  String get fbCaptureReminderNotMade =>
      'La alarma aún no se hizo. Agrégala otra vez una vez que la nota se guarde.';

  @override
  String get fbNotSavedDiscardBodyNode =>
      'La Memoria eliminará la copia que retiene. Nunca se guardó.';

  @override
  String get fbAreaNameGeneral => 'General';

  @override
  String get fbAreaNameHome => 'Casa';

  @override
  String get fbAreaNameTravel => 'Viajes';

  @override
  String get fbAreaNameUnsorted => 'Sin ordenar';

  @override
  String get fbAreaNameId => 'ID';

  @override
  String get fbModuleIndex => 'Índice de búsqueda';

  @override
  String get fbModuleMembership => 'Dispositivos';

  @override
  String get fbTriggersTitle => 'Cuando llega algo nuevo';

  @override
  String get fbTriggersSettingsDetail =>
      'Reglas que sugieren alarmas y conversaciones a partir de los mensajes que compartes';

  @override
  String get fbTriggersUnavailableTitle => 'Las reglas están apagadas aquí';

  @override
  String get fbTriggersUnavailableBody =>
      'Esta memoria no ofrece reglas. Actualízala para usarlas.';

  @override
  String get fbTriggersEmptyTitle => 'Aún no hay reglas';

  @override
  String get fbTriggersEmptyBody =>
      'Una regla vigila una clase de mensaje y solo sugiere. Nada se guarda ni se envía hasta que apruebes.';

  @override
  String get fbTriggersNew => 'Regla nueva';

  @override
  String fbTriggersGroupShare(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reglas',
      one: '1 regla',
    );
    return 'Texto que compartes · $_temp0';
  }

  @override
  String fbTriggersGroupChat(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reglas',
      one: '1 regla',
    );
    return 'Conversaciones que guardas · $_temp0';
  }

  @override
  String fbTriggersGroupNote(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reglas',
      one: '1 regla',
    );
    return 'Notas que escribes · $_temp0';
  }

  @override
  String get fbTriggersRecent => 'Mensajes recientes';

  @override
  String get fbTriggersFootnote =>
      'Una regla solo sugiere. Nada se guarda ni se envía hasta que apruebes. Los elementos ignorados se cuentan, nunca se conservan.';

  @override
  String fbRuleFinds(String template, String outcomes) {
    return 'Busca $template → $outcomes';
  }

  @override
  String get fbTemplateAppointment => 'una cita';

  @override
  String get fbTemplateDelivery => 'una entrega';

  @override
  String get fbTemplateBooking => 'una reservación';

  @override
  String get fbTemplateSubscription => 'una renovación de suscripción';

  @override
  String get fbTemplateBill => 'una cuenta por pagar';

  @override
  String get fbOutcomeReminder => 'proponer una alarma';

  @override
  String get fbOutcomeFact => 'proponer un dato';

  @override
  String get fbOutcomeConversation => 'ofrecer conversar sobre ello';

  @override
  String fbRuleWeek(int proposed, int ignored) {
    return 'Esta semana: $proposed propuestas, $ignored ignoradas';
  }

  @override
  String fbRuleWeekOffers(int proposed, int ignored, int offers) {
    String _temp0 = intl.Intl.pluralLogic(
      offers,
      locale: localeName,
      other: '$offers ofertas',
      one: '1 oferta',
    );
    return 'Esta semana: $proposed propuestas, $ignored ignoradas, $_temp0';
  }

  @override
  String get fbRulePaused => 'Pausada';

  @override
  String fbRulePause(String name) {
    return 'Pausar “$name”';
  }

  @override
  String fbRuleResume(String name) {
    return 'Reanudar “$name”';
  }

  @override
  String get fbRulePausedDone => 'Regla pausada.';

  @override
  String get fbRuleResumedDone => 'La regla volvió a encenderse.';

  @override
  String get fbSourceGroupShare => 'Texto que compartes';

  @override
  String get fbSourceGroupChat => 'Una conversación que guardas';

  @override
  String get fbSourceGroupNote => 'Una nota que escribes';

  @override
  String fbEventWhen(String source, String when) {
    return '$source · $when';
  }

  @override
  String get fbReasonMatched => 'Una regla coincidió con este mensaje.';

  @override
  String get fbReasonNoMatch => 'Ninguna regla coincidió con este mensaje.';

  @override
  String get fbReasonUncertain =>
      'La memoria no pudo leer esto con seguridad, así que no conservó nada ni sugirió nada.';

  @override
  String get fbReasonSensitive =>
      'Esto parecía privado y las notas bloqueadas están cerradas, así que se dejó de lado.';

  @override
  String get fbReasonSourceCap =>
      'Hoy vinieron demasiados mensajes de esta fuente, así que este solo se contó.';

  @override
  String get fbReasonVaultCap =>
      'Hoy llegaron demasiados mensajes, así que este solo se contó.';

  @override
  String get fbReasonRuleCap =>
      'Esta regla alcanzó su límite diario, así que este solo se contó.';

  @override
  String get fbReasonStorm =>
      'Vinieron muchos mensajes parecidos de una vez, así que se plegaron en uno.';

  @override
  String get fbReasonNoRule =>
      'No hay ninguna regla encendida para esta fuente.';

  @override
  String get fbRuleNewTitle => 'Regla nueva';

  @override
  String get fbRuleEditTitle => 'Editar la regla';

  @override
  String get fbRuleNameLabel => 'Nombre de la regla';

  @override
  String get fbRuleNameHint => 'Por ejemplo: Citas de la biblioteca';

  @override
  String get fbRuleWhen => 'Cuándo';

  @override
  String get fbRuleWhenLabel => 'De dónde viene el mensaje';

  @override
  String get fbSourceKindShare => 'Texto compartido';

  @override
  String get fbSourceKindChat => 'Conversación guardada';

  @override
  String get fbSourceKindNote => 'Nota';

  @override
  String get fbRuleSays => 'Dice algo como';

  @override
  String get fbRuleTemplateLabel => 'Clase de mensaje';

  @override
  String get fbRuleLanguageLabel => 'Idioma del mensaje';

  @override
  String get fbRuleLangEn => 'Inglés';

  @override
  String get fbRuleLangAr => 'Árabe';

  @override
  String get fbRuleTemplateHelp =>
      'La memoria lee una línea como “Cita: Visita a la biblioteca | 2026-10-15T09:00”.';

  @override
  String get fbRuleWordsLabel => 'Palabras que debe contener (opcional)';

  @override
  String get fbRuleWordsHelp =>
      'Hasta 4 palabras, separadas por comas. Texto simple, no código.';

  @override
  String get fbRuleThen => 'Entonces';

  @override
  String get fbRuleOutcomeReminder => 'Proponer una alarma';

  @override
  String get fbRuleOutcomeReminderDetail =>
      'Primero confirmas la fecha y la hora.';

  @override
  String get fbRuleOutcomeConversation => 'Ofrecer conversar sobre ello';

  @override
  String get fbRuleOutcomeConversationDetail =>
      'Tú eliges si se abre una conversación.';

  @override
  String get fbRuleOutcomeFactKept =>
      'Esta regla también propone un dato. Los datos se configuran en el servidor de memoria, y esto queda como está.';

  @override
  String get fbRuleAutoStart => 'Empezar estas conversaciones por sí solo';

  @override
  String get fbRuleAutoStartOff =>
      'Las conversaciones solo se ofrecen, nunca se arrancan por ti.';

  @override
  String get fbRuleOn => 'Esta regla está encendida';

  @override
  String get fbRuleOnDetail =>
      'Apágala para pausarla. Nada de lo que ya sugirió se elimina.';

  @override
  String get fbRuleLimit => 'Como máximo cada día';

  @override
  String fbRuleLimitValue(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count al día',
      one: '1 al día',
    );
    return '$_temp0';
  }

  @override
  String get fbRuleTest => 'Probarla con una muestra';

  @override
  String get fbRuleSampleLabel => 'Mensaje de muestra';

  @override
  String get fbRuleSampleExample => 'Usar un ejemplo';

  @override
  String get fbRuleTestRun => 'Probar';

  @override
  String get fbRuleTestAgain => 'Probar otra vez';

  @override
  String fbRuleTestMatch(String summary, String when) {
    return 'Coincide. Encontró: $summary, $when.';
  }

  @override
  String get fbRuleTestMatchPlain => 'Coincide.';

  @override
  String fbRuleTestWould(String outcomes) {
    return 'Propondría: $outcomes. Una prueba no guarda nada.';
  }

  @override
  String get fbRuleTestNoMatch => 'No coincide.';

  @override
  String get fbRuleSave => 'Guardar la regla';

  @override
  String get fbRuleSaved => 'Regla guardada.';

  @override
  String get fbRuleDelete => 'Eliminar la regla';

  @override
  String get fbRuleDeleteTitle => '¿Eliminar esta regla?';

  @override
  String fbRuleDeleteBody(String name) {
    return '“$name” deja de sugerir cosas. Lo que ya sugirió se queda para que decidas.';
  }

  @override
  String get fbRuleNeedsOwner =>
      'Este teléfono aún no puede firmar los cambios de reglas, así que las reglas se muestran pero no se cambian. Configura la aprobación para cambiarlas.';

  @override
  String get fbRuleStale =>
      'Esta regla cambió en la memoria. Se recargó: revísala y guarda otra vez.';

  @override
  String get fbRuleProblemName =>
      'Dale a la regla un nombre de hasta 80 letras.';

  @override
  String get fbRuleProblemWords =>
      'Usa hasta 4 palabras, cada una de hasta 40 letras.';

  @override
  String get fbRuleProblemOutcome =>
      'Elige al menos una cosa que la regla deba sugerir.';

  @override
  String get fbRuleProblemTemplate =>
      'Esta memoria no lee esa clase de mensaje.';

  @override
  String get fbRuleProblemLimit => 'Elige entre 1 y 20 al día.';

  @override
  String get fbEventTitle => 'Qué pasó aquí';

  @override
  String get fbEventFrom => 'De dónde vino';

  @override
  String get fbEventSender => 'Remitente (una afirmación)';

  @override
  String get fbEventApp => 'App (una afirmación)';

  @override
  String get fbEventTrust => 'Confianza';

  @override
  String get fbTrustUntrusted => 'De otra persona, sin verificar';

  @override
  String get fbTrustSelected => 'Una conversación que elegiste';

  @override
  String get fbTrustAuthored => 'Escrito por ti';

  @override
  String get fbEventRule => 'Regla que coincidió';

  @override
  String get fbEventIgnored => 'Otras notas';

  @override
  String fbEventCollapsed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se plegaron $count mensajes similares más en este.',
      one: 'Se plegó 1 mensaje similar más en este.',
    );
    return '$_temp0';
  }

  @override
  String get fbEventUnverified =>
      'Remitente sin verificar. La memoria no puede revisar quién envió de verdad este texto, así que no cambiará un dato de dinero sin tu visto bueno.';

  @override
  String get fbEventProposed => 'Lo que sugirió';

  @override
  String get fbEventFootnote =>
      'El texto original nunca se muestra aquí y nunca se sigue como instrucción.';

  @override
  String get fbTriggerChip => 'De un gatillo';

  @override
  String get fbWhyFired => 'Lo que ocurrió con este mensaje';

  @override
  String fbCareProposalWhen(String what, String when) {
    return '$what, $when';
  }

  @override
  String fbNeedsYouOffers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ofertas de gatillos',
      one: '1 oferta de un gatillo',
    );
    return '$_temp0';
  }

  @override
  String get fbOfferTitle => '¿Conversar con tu agente?';

  @override
  String fbOfferFromRule(String name) {
    return 'Oferta · $name';
  }

  @override
  String fbOfferSource(String source, String date) {
    return 'De $source · $date';
  }

  @override
  String get fbOfferTalk => 'Conversar sobre esto';

  @override
  String get fbOfferNotNow => 'Ahora no';

  @override
  String get fbOfferExplain =>
      'Una conversación nueva se abre con tu agente. Recibe un resumen corto, no el mensaje entero. Puede leer y sugerir, nunca actuar. Responder o pagar siempre te lo pregunta primero.';

  @override
  String get fbOfferChatLead =>
      'La memoria notó algo que quizá quieras conversar. Este es un resumen corto, no el mensaje original:';

  @override
  String get fbOfferStale =>
      'Esta oferta cambió o venció, así que se refrescó.';

  @override
  String get fbOfferUnknown =>
      'La memoria no pudo confirmar que la conversación empezó, así que no se abrió nada. No lo reintentará sola.';

  @override
  String get fbErrorBusy => 'La memoria está ocupada un momento. Reintenta.';

  @override
  String get fbRuleOfferUnavailable =>
      'Esta memoria no ofrece conversaciones desde las reglas.';

  @override
  String get fbNodeTitle => 'Memoria en este teléfono';

  @override
  String get fbNodeRow => 'Usar la memoria en este teléfono';

  @override
  String get fbNodeRowDetail =>
      'Conserva tu memoria en este teléfono. Nada sale del teléfono.';

  @override
  String get fbNodeUnsupportedTitle => 'No disponible aquí';

  @override
  String get fbNodeUnsupportedBody =>
      'La memoria en este teléfono necesita En este teléfono, que solo tienen los teléfonos Android.';

  @override
  String get fbNodeLinuxTitle => 'Configura primero el teléfono';

  @override
  String get fbNodeLinuxBody =>
      'La memoria corre en En este teléfono. Configúralo primero y vuelve.';

  @override
  String get fbNodeLinuxAction => 'Configurar este teléfono';

  @override
  String get fbNodeNoBuildTitle => 'Aún no hay compilación de memoria';

  @override
  String get fbNodeNoBuildBody =>
      'Esta versión de la app no puede instalar la Memoria en este teléfono. Actualiza la app a una versión más nueva, o conéctate abajo a un servidor propio.';

  @override
  String get fbNodeNotInstalledTitle => 'Memoria aún no instalada';

  @override
  String get fbNodeNotInstalledBody =>
      'La app instala la Memoria en En este teléfono, la arranca y se conecta a ella sola. Escucha solo dentro de este teléfono.';

  @override
  String get fbNodeRun => 'Configurar la memoria en este teléfono';

  @override
  String get fbNodeStepCopying => 'Instalando el programa';

  @override
  String get fbNodeStepPreparing => 'Creando la memoria';

  @override
  String get fbNodeStepStarting => 'Arrancando';

  @override
  String get fbNodeStepPairing => 'Conectando esta app';

  @override
  String get fbNodeRunningTitle => 'La memoria corre aquí';

  @override
  String get fbNodeRunningBody =>
      'La memoria corre en En este teléfono y está conectada a esta app. Escucha solo dentro de este teléfono.';

  @override
  String get fbNodeStoppedTitle => 'La memoria está detenida';

  @override
  String get fbNodeStoppedBody =>
      'Tu memoria sigue en este teléfono. Arráncala para usarla.';

  @override
  String get fbNodeStart => 'Arrancar la memoria';

  @override
  String get fbNodeStop => 'Detener la memoria';

  @override
  String get fbNodeRestart => 'Reiniciar la memoria';

  @override
  String get fbNodeStopping => 'Deteniendo';

  @override
  String get fbNodeRemoving => 'Quitando';

  @override
  String get fbNodeOpen => 'Abrir Acerca de ti';

  @override
  String get fbNodeAddress => 'Dirección';

  @override
  String get fbNodeAddressValue => 'Solo dentro de este teléfono';

  @override
  String get fbNodeRemove => 'Quitar la memoria de este teléfono';

  @override
  String get fbNodeRemoveTitle => '¿Quitar la memoria del teléfono?';

  @override
  String get fbNodeRemoveBody =>
      'Esto detiene la Memoria y elimina todo lo que guarda en este teléfono. No se puede deshacer.';

  @override
  String get fbNodeRemoveLoses1 =>
      'Cada nota, dato y alarma que la Memoria retiene aquí';

  @override
  String get fbNodeRemoveLoses2 =>
      'El vínculo de conexión y de clave de aprobación con ella';

  @override
  String get fbNodeRemoved => 'Memoria quitada de este teléfono.';

  @override
  String get fbNodeFailInstall =>
      'El programa no se pudo instalar aquí. Reintenta; Detalles dice por qué.';

  @override
  String get fbNodeFailStart => 'La memoria no arrancó. Reintenta.';

  @override
  String get fbNodeFailPair =>
      'La memoria corre, pero esta app no pudo conectarse a ella. Reintenta.';

  @override
  String get fbNodeFailStop => 'La memoria no se detuvo. Reintenta.';

  @override
  String get fbNodeFailRemove =>
      'No se pudo quitar la memoria. Quizá parte de sus datos siga en este teléfono.';

  @override
  String get fbNodeLog => 'Mostrar el log';

  @override
  String get fbReasonTargetStale =>
      'Lo que esta regla actualiza cambió, así que no se propuso nada.';

  @override
  String get fbReasonTargetGone =>
      'Lo que esta regla actualiza ya no está allí o no está abierto para ella, así que no se propuso nada.';

  @override
  String get fbReasonRejectedEvent => 'La memoria no aceptó este mensaje.';

  @override
  String get fbRuleProblemSender =>
      'El nombre del remitente debe tener de 1 a 80 letras.';

  @override
  String get fbRuleSenderLabel => 'Afirmación del remitente (opcional)';

  @override
  String get fbRuleSenderHelp =>
      'Solo mensajes que digan venir de este nombre. Cualquiera puede afirmar cualquier nombre, así que esto acota la regla; nunca prueba quién lo envió.';

  @override
  String get fbRuleAreaLabel => 'Área';

  @override
  String get fbRuleTestDraftNote =>
      'La prueba usa lo que ves aquí, guardado o no. No guarda nada.';

  @override
  String get fbCareProposalEdited => 'Cambiado. Revísalo y apruébalo.';

  @override
  String get fbCaptureRulesLook => 'Dejar que mis reglas lo lean';

  @override
  String get fbAuthReasonRules => 'Dejar que tus reglas lean este texto';

  @override
  String get fbNodeWhatTitle => '¿Qué es esto?';

  @override
  String get fbNodeWhatBody =>
      'La memoria guarda tus notas, alarmas y datos en este teléfono, privados para ti. Corre en este teléfono y nunca sale de él.';

  @override
  String get fbNodeOwnServer => 'Conectar a mi propio servidor';

  @override
  String get fbNodeOwnServerBody =>
      'Avanzado: conecta esta app a un servidor de Memory que tú corras, en lugar de usar este teléfono.';

  @override
  String get fbMemoryStopped => 'La memoria está detenida';

  @override
  String get fbMemoryStoppedDetail => 'Arráncala para ver tus notas y alarmas';

  @override
  String get fbMemoryStart => 'Arrancar';

  @override
  String get fbMemoryStarting => 'Arrancando la memoria…';

  @override
  String get fbCareTapHint => 'Toca una tarjeta para cambiarla o pasarla.';

  @override
  String fbCareApproveNamed(String claim) {
    return 'Aprobar “$claim”';
  }

  @override
  String fbCareRejectNamed(String claim) {
    return 'Rechazar “$claim”';
  }

  @override
  String fbCareEditNamed(String claim) {
    return 'Cambiar “$claim”';
  }

  @override
  String fbCareSkipNamed(String claim) {
    return 'Pasar “$claim”';
  }

  @override
  String get fbCareSkipDetail => 'Se queda aquí para después';

  @override
  String get fbCareSourceRule => 'Lo sugirió una regla';

  @override
  String get fbCareSourceAgent => 'Lo sugirió un agente';

  @override
  String get fbNodeNameThisPhone => 'la memoria de este teléfono';

  @override
  String get fbNodeNameDefault => 'esta memoria';

  @override
  String get fbStatusAllWorking => 'Todo funciona';

  @override
  String fbStatusNeedsLook(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count cosas necesitan una mirada',
      one: '1 cosa necesita una mirada',
    );
    return '$_temp0';
  }

  @override
  String fbStatusBackedUpAge(String age) {
    return 'Con respaldo · copia vieja de $age';
  }

  @override
  String get fbReminderNotSounded => 'Sin sonar en este teléfono';

  @override
  String fbEventOutcomeProposed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Sugirió $count cosas para ti',
      one: 'Sugirió 1 cosa para ti',
    );
    return '$_temp0';
  }

  @override
  String get fbEventWaitingForOk => 'Esperando tu visto bueno';

  @override
  String get fbCaptureAddTo => 'Agregar a';

  @override
  String get fbCaptureAddToNone => 'Dejar que la memoria lo ordene';

  @override
  String get fbCaptureAddToFactNote =>
      'Guardado como una línea, para que lo apruebes';

  @override
  String get fbCaptureArea => 'Área';

  @override
  String get fbCaptureAreaAuto => 'Dejar que la memoria elija';

  @override
  String get fbCaptureRemindToo => 'Recordármelo también';

  @override
  String get fbCaptureRemindTooDetail =>
      'Antes de que se guarde nada, revisas la fecha y la hora.';

  @override
  String fbCaptureRulesOn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count reglas están encendidas',
      one: '1 regla está encendida',
      zero: 'No hay reglas encendidas',
    );
    return '$_temp0';
  }

  @override
  String get fbCaptureSeeRules => 'Ver tus reglas';

  @override
  String fbCaptureFactAdded(String thing) {
    return 'Agregado a $thing.';
  }

  @override
  String get fbCaptureFactProposed =>
      'Te lo envió para tu visto bueno. Espera en Te espera.';

  @override
  String get fbCaptureSeeNeedsYou => 'Ver Te espera';

  @override
  String fbThingAddFact(String thing) {
    return 'Agregar un dato a $thing';
  }

  @override
  String get fbThingAddFactSave => 'Agregar el dato';

  @override
  String fbThingRemind(String thing) {
    return 'Recordarme sobre $thing';
  }

  @override
  String fbThingReminderMade(String thing) {
    return 'Se guardó una alarma para $thing.';
  }

  @override
  String get fbThingEditFact => 'Cambiar este dato';

  @override
  String get fbThingEditFactSave => 'Enviar mi cambio';

  @override
  String get fbThingFactChanged => 'Cambiado.';

  @override
  String get fbThingChangeProposed =>
      'Tu cambio espera tu visto bueno en Te espera.';

  @override
  String get fbThingOneLine =>
      'Una línea. Espera tu visto bueno antes de conservarse.';

  @override
  String get fbThingTapHint =>
      'Toca un dato para ver de dónde vino o para cambiarlo.';

  @override
  String fbThingExplainTold(String date) {
    return 'Tú se lo dijiste a la Memoria el $date.';
  }

  @override
  String fbThingExplainSeen(String source, String date) {
    return 'La memoria lo leyó de $source el $date. Una fuente puede estar equivocada, así que revísalo antes de fiarte de él.';
  }

  @override
  String fbThingExplainSeenNoSource(String date) {
    return 'La memoria lo leyó el $date. Una fuente puede estar equivocada, así que revísalo antes de fiarte de él.';
  }

  @override
  String fbThingExplainGuessed(String source, String date) {
    return 'La memoria lo adivinó de $source el $date. Nadie lo ha confirmado.';
  }

  @override
  String fbThingExplainGuessedNoSource(String date) {
    return 'La memoria lo adivinó el $date. Nadie lo ha confirmado.';
  }

  @override
  String fbThingExplainCalculated(String date) {
    return 'La memoria lo calculó de otros datos el $date.';
  }

  @override
  String get fbThingExplainWaiting =>
      'Está esperando tu visto bueno en Te espera.';

  @override
  String get settingsV3Servers => 'Servidores';

  @override
  String get settingsV3ThisPhone => 'Este teléfono';

  @override
  String get settingsV3Providers => 'Proveedores';

  @override
  String get settingsV3Shell => 'Shell para comandos';

  @override
  String get settingsV3VoiceTyping => 'Dictado por voz';

  @override
  String get settingsV3Permissions => 'Permisos y automatización';

  @override
  String get settingsV3Notifications => 'Notificaciones';

  @override
  String get settingsV3Background => 'Segundo plano';

  @override
  String get settingsV3Language => 'Idioma';

  @override
  String get settingsV3ByServer => 'Por servidor';

  @override
  String get settingsV3On => 'Activado';

  @override
  String get settingsV3Off => 'Desactivado';

  @override
  String get settingsV3Connected => 'Conectado';

  @override
  String get settingsV3NotConnected => 'Sin conexión';

  @override
  String get settingsV3PhoneRunning => 'Corriendo';

  @override
  String get settingsV3PhoneInstalled => 'Instalado';

  @override
  String get settingsV3PhoneSetUp => 'Configurar';

  @override
  String get settingsV3ServersBlock =>
      'Puedes cambiar algunas de estas por servidor';

  @override
  String get settingsV3ServerGone => 'Este servidor se quitó';

  @override
  String get settingsV3SectionConnection => 'Conexión';

  @override
  String get settingsV3SectionInApp => 'En esta app';

  @override
  String get settingsV3StatusVersion => 'Estado y versión';

  @override
  String get settingsV3SignIn => 'Inicio de sesión';

  @override
  String get settingsV3SignInPassword => 'Contraseña';

  @override
  String get settingsV3SignInToken => 'Token';

  @override
  String get settingsV3SignInNone => 'Sin fijar';

  @override
  String get settingsV3NameAddress => 'Nombre y dirección';

  @override
  String get settingsV3Supports => 'Lo que este servidor admite';

  @override
  String get settingsV3ModelNew => 'Modelo para conversaciones nuevas';

  @override
  String get settingsV3ServerPick => 'Lo elige el servidor';

  @override
  String get settingsV3DifferentHere => 'Diferente aquí';

  @override
  String get settingsV3Watch => 'Vigilar este servidor';

  @override
  String get settingsV3ShareRead => 'Compartir el estado leído';

  @override
  String get settingsV3Updates => 'Actualizaciones y servicio';

  @override
  String get settingsV3UseMyDefault => 'Usar mi modo por defecto';

  @override
  String get settingsV3AgentsPhone => 'En este teléfono';

  @override
  String get settingsV3ModelByServerNote =>
      'Cada servidor guarda su propia elección para conversaciones nuevas. Las conversaciones ya abiertas conservan su modelo.';

  @override
  String get settingsV3ProvidersByServerNote =>
      'Cada servidor inicia sesión en los proveedores por su cuenta. Iniciar sesión en uno nunca inicia sesión en los otros.';

  @override
  String get settingsV3ShellByServerNote =>
      'Se guarda por servidor. Otras apps que usen un servidor reciben también su shell.';

  @override
  String settingsV3ServerConnected(String name) {
    return '$name · Conectado';
  }

  @override
  String settingsV3ServersCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count servidores',
      one: '1 servidor',
    );
    return '$_temp0';
  }

  @override
  String settingsV3DifferentOn(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Diferente en $count servidores',
      one: 'Diferente en 1 servidor',
    );
    return '$_temp0';
  }

  @override
  String settingsV3ModelNewOn(String name) {
    return 'Conversaciones nuevas en $name';
  }

  @override
  String settingsV3SectionOn(String name) {
    return 'En $name';
  }

  @override
  String settingsV3SavedOn(String name) {
    return 'Se guarda en $name. Otras apps que usen $name reciben esto también.';
  }

  @override
  String settingsV3ConnectedVersion(String version) {
    return 'Conectado · $version';
  }

  @override
  String settingsV3ConnectTo(String name) {
    return 'Conectar con $name';
  }

  @override
  String settingsV3UseMyDefaultDetail(String name) {
    return 'Las notificaciones, el modelo para conversaciones nuevas y la compartición de leído de $name vuelven a tus propias elecciones.';
  }

  @override
  String get settingsV3ServerSettings => 'Ajustes del servidor';

  @override
  String get settingsV3ShellAutomatic => 'Automática';

  @override
  String get settingsV3ReadStateByServerNote =>
      'Cada servidor se pregunta por separado. Nada se comparte con un servidor hasta que lo actives para ese servidor.';

  @override
  String settingsV3SharingCount(int count, int total) {
    return '$count de $total comparten';
  }

  @override
  String get fbSettingsUsePhoneZone => 'Usar la zona horaria de este teléfono';

  @override
  String fbSettingsUsePhoneZoneDetail(String zone) {
    return '$zone. Las alarmas conservan sus horas de reloj.';
  }

  @override
  String get fbReasonKeyLockedNotYet =>
      'Tiene una contraseña o un número de cuenta, y la Memoria aún no puede guardar eso. Se queda aquí en este teléfono.';

  @override
  String get fbListLockedNoteNotYet =>
      'Las notas con contraseñas o números de cuenta aún no se guardan. Esperan en Aún sin guardar.';

  @override
  String get fbReminderNotSoundedYet =>
      'Este teléfono aún no puede hacer sonar alarmas. Se guardó y aparece en Alarmas.';

  @override
  String settingsV3ReconnectTo(String name) {
    return 'Reconectar a $name';
  }

  @override
  String get projectToolsNeedGitCommit =>
      'Los árboles de trabajo y los entornos en la nube necesitan un proyecto que git registre, con al menos un commit. Pide al agente configurar git y hacer el primer commit.';

  @override
  String settingsV3UseMyDefaultDone(String name, String what) {
    return 'Reajustado en $name a tus propias elecciones: $what.';
  }

  @override
  String get settingsV3NotificationsAllServers =>
      'Notificaciones de todos los servidores';

  @override
  String get settingsV3AgentsOnPhone => 'Agentes en este teléfono';

  @override
  String get settingsV3AgentsReady => 'Listos';

  @override
  String settingsV3AgentsExternalCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count conectados',
      one: '1 conectado',
      zero: 'Ninguno conectado',
    );
    return '$_temp0';
  }

  @override
  String settingsV3NotConnectedVersion(String version) {
    return 'Sin conexión · visto última vez con la versión $version';
  }

  @override
  String get settingsV3ConnectToChange =>
      'Conéctate para ver y cambiar sus proveedores, herramientas y permisos.';

  @override
  String quotaInAppStepChecking(String server) {
    return 'Revisando $server';
  }

  @override
  String get quotaInAppStepCopying => 'Copiando el ayudante de uso';

  @override
  String get quotaInAppStepSignIns => 'Buscando tus inicios de sesión';

  @override
  String get quotaInAppStepStarting => 'Iniciando el ayudante de uso';

  @override
  String get quotaInAppStepReading => 'Leyendo lo que queda';

  @override
  String quotaInAppFailLinux(String server) {
    return '$server aún no está listo. Termina de configurar En este teléfono y reintenta.';
  }

  @override
  String quotaInAppFailNoNode(String server) {
    return '$server aún no tiene Node, que el consumo necesita. Corre la configuración de En este teléfono otra vez para agregarlo y reintenta.';
  }

  @override
  String quotaInAppFailNodeOld(String server) {
    return 'El Node de $server es anterior a la versión 20. Corre la configuración de En este teléfono otra vez para actualizarlo y reintenta.';
  }

  @override
  String quotaInAppFailPrepare(String server) {
    return 'El consumo no se pudo preparar en $server. Reintenta; si sigue fallando, reinicia la app.';
  }

  @override
  String quotaInAppFailStart(String server) {
    return 'El ayudante de uso en $server no arrancó. Reintenta en un momento.';
  }

  @override
  String quotaInAppFailRemove(String server) {
    return 'El consumo no se pudo apagar en $server. Reintenta.';
  }

  @override
  String get settingsV3ToolsByServerNote =>
      'Las herramientas son lo que el agente puede usar, como leer archivos o correr comandos. Cada servidor guarda su propio conjunto.';

  @override
  String get launchUiUsageSignInServerGone =>
      'Ese servidor ya no está guardado, así que aquí tienes tu uso.';

  @override
  String get launchUiUsageSignInConnectionFailed =>
      'No se pudo conectar con ese servidor para iniciar sesión. Revísalo en Servidores y vuelve a probar el widget.';

  @override
  String get settingsHubSearchReasoningAliases =>
      'presentación de la transcripción razonamiento pensamiento';

  @override
  String get usageTabPlans => 'Planes';

  @override
  String get usageTabSpending => 'Gastos';

  @override
  String get usagePlansRefresh => 'Actualizar los planes';

  @override
  String get usageEmptyTitle => 'Aún no hay suscripciones';

  @override
  String get usageEmptyBody =>
      'Agrega un plan para ver cuánto queda y cuándo se reajusta.';

  @override
  String get usageAddSection => 'Agregar una suscripción';

  @override
  String usageLeft(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString% restante';
  }

  @override
  String usageUsedPercent(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return '$percentString% usado';
  }

  @override
  String get usageWindowFiveHour => '5 horas';

  @override
  String get usageWindowWeek => 'Semana';

  @override
  String get usageWindowMonth => 'Mes';

  @override
  String get usageWindowTools => 'Herramientas · mes';

  @override
  String get usageWindowOther => 'Límite';

  @override
  String usageOnConnection(String connection) {
    return 'en $connection';
  }

  @override
  String get usageThisPhone => 'Este teléfono';

  @override
  String get usageAlertsTitle => 'Alertas';

  @override
  String get usageAlertsSummaryOff => 'Apagadas';

  @override
  String get usageWidgetRow => 'Widget de la pantalla de inicio';

  @override
  String usageApiKeyOnly(String provider, String connection) {
    return 'Clave API de $provider en $connection · pagan por lo que usan, sin límites de plan';
  }

  @override
  String get usagePillSignInRanOut => 'El inicio de sesión se agotó';

  @override
  String get usagePillBroken => 'No se puede leer';

  @override
  String get usagePillCantRead => 'Aún no se puede leer';

  @override
  String get usagePillOffline => 'Sin conexión';

  @override
  String usageCantReadBody(String connection) {
    return 'Sesión iniciada en $connection. La app aún no puede leer sus límites desde allí.';
  }

  @override
  String get usageCantReadBodyOne =>
      'Sesión iniciada, pero la app aún no puede leer sus límites.';

  @override
  String usageSignInRanOut(String provider, String connection) {
    return 'El inicio de sesión de $provider en $connection se agotó';
  }

  @override
  String usageSignInRanOutOne(String provider) {
    return 'Tu inicio de sesión de $provider se agotó.';
  }

  @override
  String get usageNotSignedInAnywhere => 'Sin sesión iniciada en ningún lado';

  @override
  String get usageNotSignedInOne => 'Sin sesión iniciada';

  @override
  String usageReaderBroken(String provider) {
    return 'No se puede leer $provider ahora';
  }

  @override
  String get usageNoLimits => 'Este plan no reporta límites';

  @override
  String usageOfflineStale(String connection, String age) {
    return '$connection está sin conexión · leído hace $age';
  }

  @override
  String usageOfflineStaleOne(String age) {
    return 'Sin conexión · leído hace $age';
  }

  @override
  String usageDurationMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String usageDurationHours(int hours) {
    return '$hours h';
  }

  @override
  String usageDurationHoursMinutes(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String usageDurationDays(int days) {
    return '$days d';
  }

  @override
  String usageDurationDaysHours(int days, int hours) {
    return '$days d $hours h';
  }

  @override
  String usageResetsIn(String time, String duration) {
    return 'Se reajusta a las $time, en $duration';
  }

  @override
  String get usageResetPassed =>
      'La hora de reajuste ya pasó. Actualiza para revisar.';

  @override
  String usageResetsAt(String time) {
    return 'Se reajusta $time';
  }

  @override
  String usageFiveHourResets(String time) {
    return 'Las 5 horas se reajustan $time';
  }

  @override
  String usageWeekResets(String time) {
    return 'La semana se reajusta $time';
  }

  @override
  String usageOutUntil(String time) {
    return 'Agotado hasta $time';
  }

  @override
  String usageResetSince(String time) {
    return 'Se reajustó a las $time desde la última lectura';
  }

  @override
  String usageReadAgo(String age) {
    return 'Leído hace $age';
  }

  @override
  String usageCheckedAgo(String age) {
    return 'Revisado hace $age';
  }

  @override
  String usageReadFrom(String connection) {
    return 'Leer el consumo de $connection';
  }

  @override
  String get usageSignIn => 'Iniciar sesión';

  @override
  String get usageSignInAgain => 'Iniciar sesión otra vez';

  @override
  String get usageAddKey => 'Agregar una clave';

  @override
  String get usageTryAgain => 'Reintentar';

  @override
  String get usageSignInUnavailable =>
      'Aún no hay dónde iniciar sesión desde aquí.';

  @override
  String usageAddTitle(String provider) {
    return 'Agregar $provider';
  }

  @override
  String usageAddWhere(String provider) {
    return '¿Dónde debería iniciar sesión $provider? La app lee el consumo del que elijas.';
  }

  @override
  String get usageBest => 'El mejor';

  @override
  String get usageAddInAppNote =>
      'En este teléfono · el consumo funciona en seguida';

  @override
  String get usageAddServerNote => 'Un servidor que guardaste';

  @override
  String usageAddPhoneNote(String provider) {
    return 'Inicia sesión en el agente de $provider en este teléfono';
  }

  @override
  String usageAddNoneTitle(String provider) {
    return 'Aún no se puede agregar $provider';
  }

  @override
  String get usageAddNoneBody =>
      'Guarda un servidor, o configura En este teléfono, y aparecerá aquí.';

  @override
  String get usageReadFromTitleHere => 'Leer el consumo';

  @override
  String get usageReadFromGoneTitle => 'Esa conexión ya no existe';

  @override
  String get usageReadFromGoneBody => 'Vuelve a Planes para ver lo que queda.';

  @override
  String usageReadFromSignedIn(String providers, String connection) {
    return '$providers con sesión iniciada en $connection';
  }

  @override
  String get usageReadFromBodyInApp =>
      'Para mostrar sus límites, la app inicia un pequeño lector en En este teléfono. Solo lee cuánto queda, nunca tus conversaciones ni tus claves.';

  @override
  String get usageReadFromSetUpInApp => 'Configurar el consumo';

  @override
  String get usageNotNow => 'Ahora no';

  @override
  String get usageSubGoneTitle => 'Este plan ya no existe';

  @override
  String get usageSubGoneBody => 'Vuelve a Planes para ver lo que queda.';

  @override
  String get usageStateSignedIn => 'Sesión iniciada';

  @override
  String get usageStateExpired => 'El inicio de sesión se agotó';

  @override
  String get usageStateNotSignedIn => 'Sesión no iniciada';

  @override
  String get usageConnOffline => 'Sin conexión';

  @override
  String get usageConnReadsHere => 'Los números vienen de aquí';

  @override
  String get usageSameAccount => 'La misma cuenta';

  @override
  String get usageSignedInOn => 'Sesión iniciada en';

  @override
  String get usageAlertAt => 'Avísame en';

  @override
  String usageAlertAtPlan(String provider, int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return 'Solo para $provider. Los demás planes usan $percentString%.';
  }

  @override
  String get usageAlertAtSame => 'Igual que tus otros planes';

  @override
  String usageAlertSameAsOthers(int percent) {
    final intl.NumberFormat percentNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String percentString = percentNumberFormat.format(percent);

    return 'Igual que los demás planes ($percentString%)';
  }

  @override
  String get usageNotifyReset => 'Avisarme cuando se reajuste';

  @override
  String get usageNotifyResetHelp =>
      'Un mensaje cuando un límite agotado vuelva.';

  @override
  String get usageSaveFailed =>
      'No se pudo guardar esto. Se queda como estaba.';

  @override
  String get usageDetails => 'Detalles';

  @override
  String get usageDetailReader => 'Lector';

  @override
  String get usageDetailRead => 'Leído';

  @override
  String get usageDetailChecked => 'Revisado';

  @override
  String get usageDetailAccount => 'Cuenta';

  @override
  String get usageDetailWindows => 'Ventanas';

  @override
  String get usageYes => 'Sí, hay una cuenta conocida';

  @override
  String get usageNo => 'No se conoce ninguna cuenta';

  @override
  String get usageAlertsSwitch => 'Alertas de consumo';

  @override
  String get usageAlertsDifferent => 'Diferente del valor por defecto';

  @override
  String get usageAlertOverrideResetOn => 'te avisa cuando se reajusta';

  @override
  String get usageAlertOverrideResetOff => 'sin mensaje de reajuste';

  @override
  String get usageAlertsNothingToWatch =>
      'Aún no hay nada que vigilar. Las alertas empiezan cuando un plan se puede leer.';

  @override
  String get usageWidgetSettingsShow => 'Mostrar estos planes';

  @override
  String get usageWidgetSettingsWindows => 'Mostrar 5 horas, semanal o ambos';

  @override
  String get usageWidgetWindowBoth => 'Ambos';

  @override
  String get usageWidgetWindowFiveHour => '5 horas';

  @override
  String get usageWidgetWindowWeekly => 'Semanal';

  @override
  String get usageWidgetSettingsSetup =>
      'Mostrar las filas que necesitan configuración';

  @override
  String get usageWidgetSettingsSetupHelp =>
      'Iniciar sesión, agregar clave y configurar filas';

  @override
  String get usageSources => 'Fuentes';

  @override
  String get usageNoKey => 'Sin clave agregada';

  @override
  String get usageStateCouldntCheck => 'No se pudo revisar';

  @override
  String usageViaOpenCodeNoPlan(String connection) {
    return 'Anthropic en $connection · sin límites de plan a través de OpenCode';
  }

  @override
  String get usageNotReadYet => 'Aún no leído';

  @override
  String get usageConnectionsHeader => 'Conexiones';

  @override
  String get usageReadUsage => 'Leer el consumo';

  @override
  String usageSetupSignedInOn(String connection) {
    return 'Sesión iniciada en $connection';
  }

  @override
  String get usageSetupSignedIn => 'Sesión iniciada';

  @override
  String usageSetupRanOutOn(String connection) {
    return 'El inicio de sesión se agotó en $connection';
  }

  @override
  String get usageSetupRanOut => 'El inicio de sesión se agotó';

  @override
  String get usageSetupCouldntRead => 'No se pudo leer';

  @override
  String get usageNeedsSetupHeader => 'Necesita configuración';

  @override
  String get usageNoNumbersYet =>
      'Aún no hay números de plan. Configura uno abajo.';

  @override
  String usageComputerExplain(String server) {
    return 'La app corre una pequeña revisión de solo lectura en $server a través de OpenCode cada vez que actualiza. Nada se instala y nada queda corriendo. Devuelve solo cuánto queda, nunca tus claves ni tus conversaciones.';
  }

  @override
  String get usageComputerWhatRuns => 'Qué corre';

  @override
  String get usageComputerCommandLabel => 'Comando';

  @override
  String get usageComputerReadsLabel => 'Lee';

  @override
  String usageComputerReadsValue(String server) {
    return 'El archivo de inicio de sesión de OpenCode en $server, y el del Codex CLI para Codex. Solo lectura; nada se escribe.';
  }

  @override
  String get usageComputerTurnOn => 'Encender';

  @override
  String get usageComputerTurnOff => 'Apagar';

  @override
  String get usageComputerReadNow => 'Leer ahora';

  @override
  String get usageComputerDone => 'Listo';

  @override
  String usageComputerChecking(String server) {
    return 'Revisando $server…';
  }

  @override
  String usageComputerFound(String plans, String server) {
    return 'Se encontraron $plans en $server.';
  }

  @override
  String usageComputerFoundNone(String server) {
    return 'La revisión funcionó, pero no hay ningún plan con sesión iniciada en $server.';
  }

  @override
  String usageComputerOnNote(String server) {
    return 'Leyendo $server a través de OpenCode.';
  }

  @override
  String usageComputerNeedsNode(String server) {
    return '$server necesita Node.js 20 o más nuevo.';
  }

  @override
  String usageComputerServerTooOld(String server) {
    return 'OpenCode en $server es demasiado viejo para esto. Actualízalo y reintenta.';
  }

  @override
  String usageComputerOffline(String server) {
    return 'No se puede alcanzar $server ahora mismo.';
  }

  @override
  String usageComputerNoAnswer(String server) {
    return 'La revisión en $server no terminó. Reintenta.';
  }

  @override
  String usageComputerRowTitle(String server) {
    return 'Leyendo $server';
  }

  @override
  String get usageDetailLastCheck => 'Última revisión';

  @override
  String get usagePhoneNotRunning => 'Sin correr';

  @override
  String get kitRequestNeedsYouLabel => 'Te necesita';

  @override
  String get kitRequestAlwaysAllowLike =>
      'Permitir siempre solicitudes como esta';

  @override
  String get chatRequestAlwaysAllowCommands =>
      'Permitir siempre comandos como este';

  @override
  String get chatRequestAskCommand => '¿Ejecutar este comando?';

  @override
  String get chatRequestAskEdit => '¿Editar este archivo?';

  @override
  String get chatRequestAskRead => '¿Leer este archivo?';

  @override
  String get chatRequestAskDirectory => '¿Abrir esta carpeta?';

  @override
  String get chatRequestAskFetch => '¿Abrir esta página web?';

  @override
  String usageBackAt(String time) {
    return 'De vuelta a las $time';
  }

  @override
  String get usageAlertsOnWord => 'Encendidas';

  @override
  String usageWidgetPlans(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count planes',
      one: '1 plan',
      zero: 'Sin planes',
    );
    return '$_temp0';
  }

  @override
  String get usageAlertsForEvery => 'Para cada plan que la app puede leer';

  @override
  String get usageAlertsTellMe => 'Avísame cuando';

  @override
  String get usageAlertsQuietGroup => 'Horas tranquilas';

  @override
  String get usageAlertPlanDownTo => 'Un plan queda en';

  @override
  String get usageAlertUsedUpBack => 'Un plan agotado vuelve';

  @override
  String get usageQuietSameAsOthers => 'Igual que tus otras alertas';

  @override
  String get usageWifiOnlyShort => 'Revisar solo con Wi-Fi';

  @override
  String kitTaskChipCount(int done, int total) {
    return '$done/$total';
  }

  @override
  String kitTaskChipDone(int done, int total) {
    return '✓ $done/$total';
  }

  @override
  String kitTaskChipAdded(int done, int total) {
    return '+ $done/$total';
  }

  @override
  String kitTaskChipSpoken(int done, int total) {
    return 'Tareas, $done de $total hechas';
  }

  @override
  String kitTaskChipSpokenNow(int done, int total, String task) {
    return 'Tareas, $done de $total hechas, ahora $task';
  }

  @override
  String get toolCardUpdatedTasks => 'Tareas actualizadas';

  @override
  String kitDiffFoldAbove(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas arriba',
      one: '1 línea arriba',
    );
    return '… $_temp0';
  }

  @override
  String kitDiffFoldBelow(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas abajo',
      one: '1 línea abajo',
    );
    return '… $_temp0';
  }

  @override
  String kitDiffFoldBetween(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas',
      one: '1 línea',
    );
    return '… $_temp0';
  }

  @override
  String notifStatusNeedsYou(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count te necesitan',
      one: '1 te necesita',
    );
    return '$_temp0';
  }

  @override
  String notifStatusStillWorking(int count) {
    return '$count siguen en curso';
  }

  @override
  String notifStatusChatsWorking(int count) {
    return '$count conversaciones en curso';
  }

  @override
  String notifStatusMore(int count) {
    return '+$count más';
  }

  @override
  String notifStatusWaitingCount(int count) {
    return '$count en espera';
  }

  @override
  String notifStatusTasksProgress(int done, int total) {
    return '$done de $total tareas';
  }

  @override
  String notifStatusMinutes(int count) {
    return '$count min';
  }

  @override
  String notifStatusHours(int count) {
    return '$count h';
  }

  @override
  String notifStatusWaitingProvider(String provider) {
    return 'En espera · límite de $provider';
  }

  @override
  String get notifStatusWaitingGeneric =>
      'En espera · el proveedor está ocupado';

  @override
  String notifStatusTriesAgainAt(String time) {
    return 'Reintenta a las $time';
  }

  @override
  String get notifStatusTriesAgainSoon => 'Reintentará pronto';

  @override
  String get notifStatusPausedAndroidTitle =>
      'En pausa · límite de segundo plano de Android';

  @override
  String get notifStatusPausedAndroidBody =>
      'Android detiene el trabajo en segundo plano tras 6 horas al día. Abre OpenCode para continuar.';

  @override
  String get notifStatusPausedHotTitle =>
      'En pausa · el teléfono está caliente';

  @override
  String get notifStatusPausedHotBody => 'Se reanuda cuando se enfríe';

  @override
  String get notifStatusPausedIdleTitle => 'En pausa · nada en ejecución';

  @override
  String get notifStatusPausedIdleBody => 'Toca para iniciar OpenCode de nuevo';

  @override
  String notifStatusReconnectingTitle(String server) {
    return 'Reconectando con $server';
  }

  @override
  String get notifStatusReconnectingGeneric => 'Reconectando';

  @override
  String get notifStatusReconnectingBody => 'Esperando la conexión';

  @override
  String get notifStatusReadyTitle => 'Listo · nada en ejecución';

  @override
  String get notifStatusReadyBody => 'Listo cuando quieras';

  @override
  String notifStatusLastReply(String ago) {
    return 'Última respuesta hace $ago';
  }

  @override
  String get notifStatusLastReplyNow => 'Última respuesta hace un momento';

  @override
  String notifStatusSetupTitle(int step, int steps) {
    return 'Configuración · $step de $steps';
  }

  @override
  String notifStatusOnServer(String server) {
    return 'en $server';
  }

  @override
  String get notifStatusOnThisPhone => 'en este teléfono';

  @override
  String notifStatusPublicWorking(int count) {
    return '$count en curso';
  }

  @override
  String get notifStatusPublicWaiting => 'Esperando';

  @override
  String get notifStatusPublicPaused => 'En pausa';

  @override
  String get notifStatusPublicReconnecting => 'Reconectando';

  @override
  String get notifStatusPublicReady => 'Listo';

  @override
  String get notifStatusPublicSetup => 'Configurando';

  @override
  String get integrationsSignInCodeLabel => 'Tu código';

  @override
  String get integrationsSignInCodeHint =>
      'Abrir el navegador copia el código por ti. Pégalo en la página que se abre.';

  @override
  String get integrationsCodeCopied => 'Código copiado';

  @override
  String get integrationsCopyCode => 'Copiar código';

  @override
  String get integrationsOpenBrowserAgain => 'Abrir el navegador de nuevo';

  @override
  String get integrationsSignInWaitingBrowser =>
      'Esperando la aprobación en el navegador';

  @override
  String get integrationsSignInWaitingDetail =>
      'Ingresa el código en tu navegador y aprueba. Esta app está esperando y termina el inicio de sesión sola cuando vuelvas.';

  @override
  String get integrationsSignInEnded => 'El inicio de sesión terminó';

  @override
  String get integrationsSignInEndedDetail =>
      'Este inicio de sesión terminó antes de ser aprobado. Puede que el código haya caducado o que el servidor se haya reiniciado. No se agregó nada.';

  @override
  String get integrationsStartAgain => 'Empezar de nuevo';

  @override
  String get mentionToolTitle => 'Archivo del proyecto';

  @override
  String get mentionToolSupporting =>
      'Indica al agente un archivo de este proyecto';

  @override
  String get mentionSheetTitle => 'Archivos del proyecto';

  @override
  String get mentionSearchLabel => 'Buscar un archivo por nombre';

  @override
  String get mentionSearchHint =>
      'Escribe parte del nombre de un archivo para buscar en este proyecto.';

  @override
  String get mentionSearchFailedTitle => 'La búsqueda no terminó';

  @override
  String get mentionSearchFailedMessage =>
      'Revisa la conexión con el servidor y reintenta.';

  @override
  String get mentionNoMatchWhat => 'archivos de proyecto';

  @override
  String get mentionProjectFolder => 'Carpeta del proyecto';

  @override
  String get inAppServerOwnershipRecoveryBody =>
      'El servidor de este teléfono no pudo reiniciarse de forma segura. En los ajustes de la app, toca Forzar detención y vuelve a abrir la app; tus conversaciones se conservan.';

  @override
  String get liveStyleAurora => 'Deriva de aurora';

  @override
  String get liveStyleMesh => 'Malla que respira';

  @override
  String get liveStyleDotRipple => 'Ondas de puntos';

  @override
  String get liveStyleTopo => 'Líneas de contorno';

  @override
  String get liveStyleFlowThreads => 'Hilos de flujo';

  @override
  String get liveStyleGlowSpill => 'Derrame de brillo';

  @override
  String get liveStyleLightRays => 'Rayos de luz';

  @override
  String get liveStyleConstellation => 'Constelación';

  @override
  String get liveStyleHalftone => 'Semitonos';

  @override
  String get liveStyleLiquidBlobs => 'Gotas líquidas';

  @override
  String get liveStyleMatrixRain => 'Lluvia matrix';

  @override
  String get liveStyleBlueprint => 'Plano';

  @override
  String get liveStyleCircuit => 'Circuito';

  @override
  String get liveStyleOscilloscope => 'Osciloscopio';

  @override
  String get liveStyleRadar => 'Radar';

  @override
  String get liveStyleHoneycomb => 'Panel de abejas';

  @override
  String get liveStyleBrushedMetal => 'Metal cepillado';

  @override
  String get liveStyleSilk => 'Seda';

  @override
  String get liveStyleCaustics => 'Cáusticas';

  @override
  String get liveStyleBokeh => 'Desenfoque bokeh';

  @override
  String get liveIntensitySubtle => 'Sutil';

  @override
  String get liveIntensityNormal => 'Normal';

  @override
  String get liveIntensityBold => 'Intensa';

  @override
  String get effectsLiveBackground => 'Fondo animado';

  @override
  String get continueWithMenu => 'Continuar con…';

  @override
  String get continueWithMenuHint =>
      'Lleva este trabajo a otro agente o modelo';

  @override
  String get continueWithTitle => 'Continuar este trabajo';

  @override
  String continueWithSubtitle(String title, String project) {
    return '$title · $project';
  }

  @override
  String get continueWithModelsHere => 'Modelos en este servidor';

  @override
  String get continueWithAgents => 'Agentes en este teléfono';

  @override
  String get continueWithNothingReady =>
      'Ningún otro agente o modelo está listo para tomar el relevo.';

  @override
  String get continueWithLoadFailed =>
      'No se pudieron cargar los agentes. Reintenta.';

  @override
  String get continueWithTryAgain => 'Reintentar';

  @override
  String get continueWithSignIn => 'Iniciar sesión';

  @override
  String continueWithNeedsSignIn(String agent) {
    return 'Inicia sesión para usar $agent.';
  }

  @override
  String continueWithLimitReached(String agent) {
    return '$agent alcanzó su límite por ahora.';
  }

  @override
  String continueWithNotReady(String agent) {
    return '$agent aún no está listo en este teléfono.';
  }

  @override
  String get continueWithNativeNote => 'Tu conversación se queda aquí.';

  @override
  String get continueWithUseModel => 'Usar este modelo';

  @override
  String get continueWithModelFailed =>
      'No se pudo cambiar el modelo. Reintenta.';

  @override
  String get continueWithNewNote =>
      'Empieza una conversación nueva con un registro de esta.';

  @override
  String get continueWithSourceStays => 'Esta conversación sigue disponible.';

  @override
  String get continueWithFactAgent => 'Agente';

  @override
  String get continueWithFactModel => 'Modelo';

  @override
  String get continueWithFactProject => 'Proyecto';

  @override
  String get continueWithPreparing =>
      'Preparando el registro de la conversación…';

  @override
  String get continueWithSaving => 'Guardando el registro de la conversación…';

  @override
  String get continueWithStarting => 'Empezando la conversación…';

  @override
  String get continueWithLinking => 'Vinculando las conversaciones…';

  @override
  String get continueWithRecordTitle => 'Registro de la conversación';

  @override
  String continueWithRecordSize(int kb) {
    return 'Cerca de $kb KB';
  }

  @override
  String get continueWithOlderLeftOut =>
      'Los turnos más viejos se dejaron fuera para mantener pequeño el registro';

  @override
  String continueWithOmittedCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count mensajes dejados fuera',
      one: '1 mensaje dejado fuera',
    );
    return '$_temp0';
  }

  @override
  String get continueWithRedacted =>
      'Todo lo que parecía contraseña o clave quedó oculto.';

  @override
  String get continueWithReadPreview =>
      'Lee primero la vista previa. No puede atrapar cada detalle privado.';

  @override
  String get continueWithShowPreview => 'Mostrar la vista previa';

  @override
  String get continueWithHidePreview => 'Ocultar la vista previa';

  @override
  String continueWithRecordFile(String path) {
    return 'Guardado como $path';
  }

  @override
  String get continueWithFirstMessage => 'Primer mensaje';

  @override
  String get continueWithFirstMessageHelp =>
      'Conserva el nombre del archivo para que el agente pueda leer el registro.';

  @override
  String get continueWithMessageInvalid =>
      'Escribe un primer mensaje que conserve el nombre del archivo del registro.';

  @override
  String get continueWithStart => 'Empezar y continuar';

  @override
  String get continueWithCancel => 'Cancelar';

  @override
  String get continueWithPrepareAgain => 'Preparar otra vez';

  @override
  String get continueWithSaveLinkAgain => 'Guardar el vínculo otra vez';

  @override
  String get continueWithOpenConversation => 'Abrir la conversación';

  @override
  String get continueWithAnother => 'Continuar con otro agente';

  @override
  String get continueWithErrNoWriter =>
      'Este agente aún no puede guardar un registro de conversación aquí.';

  @override
  String get continueWithErrProject =>
      'Estas conversaciones no están conectadas al mismo proyecto.';

  @override
  String get continueWithErrTarget =>
      'Este modelo no está disponible. Elige otro.';

  @override
  String get continueWithErrSource =>
      'Esta conversación cambió. Prepara el registro otra vez.';

  @override
  String get continueWithErrScope =>
      'El servidor o el proyecto cambió. Empieza otra vez.';

  @override
  String get continueWithErrRead =>
      'No se pudo leer la conversación. Reintenta prepararla.';

  @override
  String get continueWithErrTooLarge =>
      'El último turno es demasiado grande para llevarlo.';

  @override
  String get continueWithErrFile =>
      'No se pudo guardar el registro. No se envió ningún mensaje.';

  @override
  String get continueWithErrUnknown =>
      'El resultado no está confirmado. Revisa las conversaciones de destino antes de reintentar.';

  @override
  String get continueWithErrLink =>
      'La conversación empezó, pero su vínculo no se pudo guardar.';

  @override
  String get continueWithErrGeneric =>
      'No se pudo preparar el registro. Reintenta.';

  @override
  String continuedIn(String agent) {
    return 'Se continuó en $agent';
  }

  @override
  String continuedFrom(String title) {
    return 'Se continuó desde “$title”';
  }

  @override
  String continuedFromShort(String title) {
    return 'desde $title';
  }

  @override
  String continuedInShort(String agent) {
    return 'se continuó en $agent';
  }

  @override
  String get continuedGoneShort => 'la otra conversación ya no existe';

  @override
  String get continuedGone => 'Esta conversación ya no está disponible.';

  @override
  String get continueWithChange => 'Cambiar';

  @override
  String get workServerBusyPhone => 'OpenCode en este teléfono está ocupado';

  @override
  String get workServerRestartBodyCommand =>
      'El comando en ejecución también se detendrá. Tus conversaciones se conservan.';

  @override
  String get connectionReconnectingQuiet => 'Reconectando…';

  @override
  String get connectionTryNow => 'Intentar ahora';

  @override
  String kitComposerWaitsForAgent(String agent) {
    return 'Se envía cuando $agent responda';
  }

  @override
  String get textSizeTitle => 'Tamaño del texto';

  @override
  String get textSizeSmallest => 'Muy pequeño';

  @override
  String get textSizeSmall => 'Pequeño';

  @override
  String get textSizeDefault => 'Predeterminado';

  @override
  String get textSizeLarge => 'Grande';

  @override
  String get kitComposerAddToTurnNote =>
      'El agente leerá esto mientras trabaja.';

  @override
  String get continuedOpenFromList =>
      'Encuéntrala en las conversaciones de ese servidor.';

  @override
  String get teamModelsSection => 'Modelos';

  @override
  String get teamModelsNote =>
      'Cada trabajo nuevo empieza con el modelo elegido para su rol. Una elección guardada aplica a trabajos nuevos, no a los que ya corren.';

  @override
  String get teamModelServerDefault => 'Por defecto del servidor';

  @override
  String get teamModelServerDefaultHint =>
      'Usa el modelo al que este servidor recurre cuando no se elige uno. Puede diferir del modelo que usan tus conversaciones.';

  @override
  String get teamModelPlannerSaved =>
      'Modelo del planificador guardado. Pide otra vez para planificar con él.';

  @override
  String get teamProjectEditorTypePath => 'Escribir una ruta';

  @override
  String get teamProjectEditorChooseFolder => 'Elegir una carpeta';

  @override
  String get teamProjectEditorFolderNone => 'Aún no se eligió ninguna carpeta';

  @override
  String get teamProjectEditorFirstCommit => 'Hacer el primer commit por mí';

  @override
  String get teamProjectEditorFirstCommitManual =>
      'Abre una terminal en esa carpeta y corre: git commit --allow-empty -m \"First commit\"';

  @override
  String get teamProjectEditorFirstCommitFailed =>
      'El primer commit no se pudo hacer. Abre una terminal en esa carpeta y corre: git commit --allow-empty -m \"First commit\"';

  @override
  String get teamProjectEditorStarting =>
      'Revisando la carpeta y arrancando el planificador…';

  @override
  String get teamProjectEditorDraftRestored => 'Tu borrador sin enviar volvió.';

  @override
  String get teamProjectEditorStartFresh => 'Empezar de cero';

  @override
  String get teamProjectEditorStarterNote =>
      'Se redactó un primer hito a partir de tu tarea. Edítalo antes de aprobar.';

  @override
  String get teamRefusalRepoPathInvalid =>
      'El equipo no puede encontrar esa carpeta en este teléfono.';

  @override
  String get teamRefusalRepoPathInvalidNext =>
      'Elige la carpeta de la lista en vez de escribirla.';

  @override
  String effectsLookPreviewLabel(String name) {
    return 'Vista previa de $name. Abre los aspectos.';
  }

  @override
  String get effectsLookPreviewOffLabel =>
      'El fondo en vivo está apagado. Abre los aspectos.';

  @override
  String get effectsLookPreviewOff => 'Apagado';

  @override
  String get teamProjectEditorFolderEmpty =>
      'Esta carpeta aún no tiene commits, y el equipo necesita uno desde donde empezar.';

  @override
  String get teamProjectEditorChooseModeBudget =>
      'Elige Un solo carril o Agentes en paralelo, y pon un presupuesto o elige Sin límite.';

  @override
  String get teamProjectEditorFallbackSection => 'Opcional';

  @override
  String get teamProjectEditorFallbackNote =>
      'Se guarda con el rol, pero el equipo aún no se cambia a él automáticamente.';

  @override
  String get teamUiRowChecking => 'Revisando…';

  @override
  String get teamRefusalCheckerUnreadable =>
      'La revisión terminó, pero su respuesta no se pudo leer.';

  @override
  String get teamRefusalCheckerUnreadableNext =>
      'Reanudar lee esa respuesta otra vez sin preguntar al modelo. Si el trabajo pasa, se fusiona a dev, y las tareas que esperan detrás de ella pueden empezar.';

  @override
  String get teamProjectInterruptedResumeNote =>
      'Reanudar lee la revisión terminada otra vez sin preguntar al modelo. Si el trabajo pasa, se fusiona a dev, y las tareas que esperan detrás de ella pueden empezar.';

  @override
  String get teamProjectInterruptedResume => 'Reanudar';

  @override
  String get widgetV2ConfigureTitle => 'Estilo del widget';

  @override
  String get widgetProjectTitle => 'Mostrar conversaciones de';

  @override
  String get widgetProjectUnavailable =>
      'No se pudo guardar esta elección. Reintenta.';

  @override
  String get widgetV2BackgroundLabel => 'Fondo';

  @override
  String get widgetV2BackgroundSolid => 'Sólido';

  @override
  String get widgetV2BackgroundTinted => 'Teñido';

  @override
  String get widgetV2BackgroundTransparent => 'Transparente';

  @override
  String get widgetV2AccentLabel => 'Color de énfasis';

  @override
  String get widgetV2AccentWallpaper => 'Fondo de pantalla';

  @override
  String get widgetV2AccentApp => 'Tema de la app';

  @override
  String get widgetV2AccentGreen => 'Verde';

  @override
  String get widgetV2AccentTeal => 'Turquesa';

  @override
  String get widgetV2AccentBlue => 'Azul';

  @override
  String get widgetV2AccentAmber => 'Ámbar';

  @override
  String get widgetV2SaveStyle => 'Guardar estilo';

  @override
  String get widgetV2SteerTitle => 'Agregar a este turno';

  @override
  String get widgetV2AfterReplyTitle => 'Enviar tras esta respuesta';

  @override
  String get widgetV2VoiceTitle => 'Mensaje nuevo';

  @override
  String get widgetV2MessageHint => 'Escribe un mensaje';

  @override
  String get widgetV2Dictate => 'Dictar un mensaje';

  @override
  String get widgetV2Send => 'Enviar';

  @override
  String get widgetV2Sending => 'Enviando…';

  @override
  String get widgetV2Accepted => 'Enviado';

  @override
  String get widgetV2StaleRequest =>
      'La conversación cambió. Tu mensaje sigue aquí.';

  @override
  String get widgetV2Unavailable =>
      'La conexión en segundo plano no está disponible. Tu mensaje sigue aquí.';

  @override
  String get widgetV2Unsupported => 'Esta conexión no puede hacer eso.';

  @override
  String get widgetV2Uncertain =>
      'No pudimos confirmar si se envió. Revisa la conversación antes de enviar de nuevo.';

  @override
  String get widgetV2Busy => 'Espera a que termine la acción actual.';

  @override
  String get widgetV2InvalidRequest => 'Escribe un mensaje antes de enviar.';

  @override
  String get widgetV2UnknownBehavior =>
      'Esperando los detalles de la conversación…';

  @override
  String get widgetV2Cancel => 'Cancelar';

  @override
  String get widgetV2PreviewLabel => 'Vista previa del widget';

  @override
  String get widgetV2PreviewTitle => 'Arreglar la página de inicio de sesión';

  @override
  String get widgetV2PreviewSecond => 'Escribir las notas de la versión';

  @override
  String get widgetV2PreviewStep => 'Editando archivos';

  @override
  String get widgetV2PreviewWorking => 'Trabajando';

  @override
  String get widgetV2PreviewStop => 'Detener';

  @override
  String get widgetV2PreviewSteer => 'Guiar';

  @override
  String get widgetV2PreviewAllow => 'Permitir';

  @override
  String get widgetV2SizeLabel => 'Tamaño de la vista previa';

  @override
  String get widgetV2SizeCompact => 'Pequeño';

  @override
  String get widgetV2SizeCard => 'Mediano';

  @override
  String get widgetV2SizeList => 'Ancho';

  @override
  String get widgetV2StyleStale =>
      'Este widget cambió. Ciérralo e inténtalo de nuevo.';

  @override
  String get widgetV2StyleUnavailable =>
      'No se pudo guardar el estilo. Inténtalo de nuevo.';

  @override
  String get widgetV2DictationUnavailable =>
      'El dictado no está disponible ahora. Puedes escribir.';

  @override
  String get widgetV2RestartUnavailable =>
      'Esta conversación solo se puede cambiar desde la app.';

  @override
  String get phoneSetupErrorPackageRepair =>
      'Algunos paquetes de Linux necesitan reparación. Continúa la configuración para intentarlo de nuevo. Los detalles muestran qué lo detuvo.';

  @override
  String get agentsFailNeedsSignIn =>
      'Claude Code necesita que inicies sesión en este teléfono.';

  @override
  String get agentsFailHelperVersion =>
      'Paseo necesita una actualización antes de que este agente pueda conectarse. Actualízalo e inténtalo de nuevo.';

  @override
  String get agentsFailTimeout =>
      'El teléfono tardó demasiado en terminar la comprobación. Reintenta cuando esté menos ocupado.';

  @override
  String get agentsCheckRetry => 'Intentar de nuevo';

  @override
  String get agentsCheckUpdateHelper => 'Actualiza Paseo e inténtalo de nuevo';

  @override
  String get agentsCheckPrevious => 'Usar la versión anterior';

  @override
  String agentsCheckPreviousNote(String version) {
    return 'La versión $version sigue en este teléfono. Se comprobará antes de que la uses. Tu sesión y tus conversaciones se conservan.';
  }

  @override
  String get agentsCheckAgentChanged =>
      'Cambió la versión requerida del agente. Comprueba este teléfono de nuevo.';

  @override
  String get agentsCheckHelperChanged =>
      'Cambió la versión requerida de Paseo. Comprueba la conexión de nuevo.';

  @override
  String get agentsCheckInstallChanged =>
      'La configuración del teléfono cambió. Compruébala de nuevo.';

  @override
  String agentsCheckDownload(
    String component,
    String downloaded,
    String total,
  ) {
    return 'Descargando $component · $downloaded de $total';
  }

  @override
  String agentsCheckDownloadUnknown(String component, String downloaded) {
    return 'Descargando $component · $downloaded';
  }

  @override
  String agentsCheckDownloading(String component) {
    return 'Descargando $component';
  }

  @override
  String agentsCheckPreparing(String component) {
    return 'Preparando $component';
  }

  @override
  String get agentsCheckPhoneTools => 'Herramientas del teléfono';

  @override
  String get phoneSetupErrorInterrupted =>
      'La configuración se detuvo antes de terminar. Continúa para intentarlo de nuevo. Los pasos completados se conservan.';

  @override
  String get agentErrorProviderAuth =>
      'El proveedor del modelo necesita un inicio de sesión válido.';

  @override
  String get agentErrorProviderAuthHint =>
      'Abre los ajustes del proveedor para iniciar sesión o reemplazar la clave de API, y vuelve a intentarlo.';

  @override
  String get agentErrorUnknown => 'La solicitud no pudo terminar.';

  @override
  String get agentErrorUnknownHint =>
      'Revisa la conversación antes de enviar de nuevo. Los detalles pueden ayudar a explicar qué pasó.';

  @override
  String get serverTrustHttpTitle => '¿Usar HTTP sin cifrar?';

  @override
  String get serverTrustHttpPublicBody =>
      'En esta dirección HTTP pública, tu contraseña y código viajan sin cifrar y otros en la red pueden leerlos o modificarlos. Es arriesgado en Wi-Fi público; prefiere HTTPS o una conexión privada de Tailscale.';

  @override
  String get serverTrustHttpAccept => 'Permitir HTTP para este servidor';

  @override
  String get serverTrustCertificateTitle => '¿Confiar en este certificado?';

  @override
  String get serverTrustCertificateBody =>
      'Esta conexión está cifrada, pero este dispositivo no puede verificar el emisor del certificado ni el nombre del servidor. Compara esta huella SHA-256 con la de tu servidor por otro canal de confianza y confía solo si coinciden; las próximas conexiones exigirán este mismo certificado.';

  @override
  String get serverTrustCertificateChangedTitle =>
      'El certificado del servidor cambió';

  @override
  String get serverTrustCertificateChangedBody =>
      'El certificado ya no coincide con el que aceptaste, así que la app bloqueó la conexión antes de enviar tu contraseña. Puede ser una renovación o una conexión interceptada; vuelve a comprobar la huella con el dueño del servidor antes de confiar en uno nuevo.';

  @override
  String get serverTrustCertificateInspect => 'Comprobar certificado';

  @override
  String get serverTrustCertificateRecheck =>
      'Volver a comprobar el certificado';

  @override
  String get serverTrustCertificateAccept => 'Confiar en esta huella';

  @override
  String get serverTrustCertificateReplace => 'Confiar en el certificado nuevo';

  @override
  String get serverTrustCertificatePinned =>
      'Este servidor usa la huella del certificado en la que confiaste. Un certificado distinto bloqueará la conexión.';

  @override
  String get serverTrustCertificateFailed =>
      'No se pudo comprobar el certificado. Revisa la dirección y la red e inténtalo de nuevo. No se confió en nada.';

  @override
  String get serverTrustFingerprint => 'Huella SHA-256';

  @override
  String get serverTrustInvalidPort =>
      'Escribe un puerto de servidor entre 1 y 65535.';

  @override
  String get serverTrustHttpUnconfirmed =>
      'Este servidor usa HTTP sin cifrar y aún no lo has confirmado. Edita el servidor para revisar y aprobar la advertencia de HTTP, o usa HTTPS.';

  @override
  String get serverTrustProbeFailed =>
      'No se pudo comprobar el servidor. Revisa la dirección e inténtalo de nuevo.';

  @override
  String get serverTrustPasswordRejected =>
      'Contraseña rechazada. Revisa la contraseña del servidor e inténtalo de nuevo.';

  @override
  String serverTrustCertificateValidity(String from, String to) {
    return 'Válido del $from al $to';
  }

  @override
  String get serverTrustCertificateValidityError =>
      'El certificado del servidor ha caducado o aún no es válido. Pide al propietario del servidor que lo renueve o revisa el reloj del dispositivo antes de reconectar.';

  @override
  String get devPreviewTitle => 'Vista previa';

  @override
  String get devPreviewReload => 'Actualizar página';

  @override
  String get devPreviewOpenInBrowser => 'Abrir en el navegador';

  @override
  String get devPreviewCopyAddress => 'Copiar dirección';

  @override
  String get devPreviewLoading => 'Cargando la vista previa';

  @override
  String get devPreviewFailedTitle => 'Aún no responde nada';

  @override
  String devPreviewFailedBody(String port) {
    return 'El puerto $port no responde. Puede que la app aún esté arrancando.';
  }

  @override
  String get devPreviewRetry => 'Intentar de nuevo';

  @override
  String devPreviewRemoteNote(String port, String host) {
    return 'Mostrando el puerto $port en $host. El servidor de desarrollo debe escuchar en 0.0.0.0 (todas las direcciones) para que este teléfono pueda alcanzarlo.';
  }

  @override
  String get devPreviewNoServerTitle => 'Conecta un servidor primero';

  @override
  String get devPreviewNoServerBody =>
      'Un enlace a esta máquina solo tiene sentido en la máquina que ejecuta la app. Conecta un servidor o abre el enlace en un navegador.';

  @override
  String get devPreviewNoHostTitle => 'No se encuentra ese servidor';

  @override
  String get devPreviewNoHostBody =>
      'La dirección de este servidor está incompleta, así que el enlace no puede apuntar a él. Ábrelo en un navegador.';

  @override
  String get devPreviewUnsupportedTitle =>
      'La vista previa no está disponible aquí';

  @override
  String get devPreviewUnsupportedBody =>
      'Este dispositivo no puede mostrar la página dentro de la app. Ábrela en un navegador.';

  @override
  String get devPreviewMenu => 'Vista previa de una app en ejecución';

  @override
  String get devPreviewMenuHint =>
      'Abrir un servidor dev que mencionó esta conversación';

  @override
  String get devPreviewSheetSubtitle =>
      'Servidores que mencionó esta conversación';

  @override
  String devPreviewPortTitle(String port) {
    return 'Puerto $port';
  }

  @override
  String get devPreviewOnPhone => 'En este teléfono';

  @override
  String devPreviewOnServer(String host) {
    return 'En $host';
  }

  @override
  String get openCode1UnsupportedTitle => 'Actualiza este servidor';

  @override
  String get openCode1Unsupported =>
      'Este servidor ejecuta OpenCode 1, que esta app ya no admite. Actualízalo a OpenCode 2.';

  @override
  String get openCode2UpgradeTitle => 'Actualizando a OpenCode 2';

  @override
  String get openCode2UpgradeFailed =>
      'La actualización no pudo terminar de forma segura. Tus conversaciones y archivos de OpenCode 1 siguen en este teléfono, intactos. Inténtalo de nuevo cuando el servidor esté inactivo y haya suficiente espacio libre.';

  @override
  String get openCode2HostInstall => 'Instalar e iniciar OpenCode 2';

  @override
  String get openCode2HostInstallDetail =>
      'Necesita Node.js y npm. Arranca solo en esta computadora e imprime su contraseña. Mantén esta terminal abierta.';

  @override
  String get schedulePhoneBusy =>
      'Un mensaje programado está usando el servidor del teléfono. Reintenta cuando termine.';

  @override
  String get schedulePhoneCheckFailed =>
      'No se pudo revisar el servidor del teléfono. Reintenta.';

  @override
  String get scheduleTitle => 'Mensajes programados';

  @override
  String get scheduleCreate => 'Programar un mensaje';

  @override
  String get scheduleEdit => 'Editar programación';

  @override
  String get schedulePrompt => 'Mensaje';

  @override
  String get scheduleTarget => 'Conversación';

  @override
  String get scheduleThisConversation => 'Esta conversación';

  @override
  String get scheduleNewConversation => 'Conversación nueva en cada ejecución';

  @override
  String get scheduleOnce => 'Una vez';

  @override
  String get scheduleRepeat => 'Cuándo';

  @override
  String get scheduleTime => 'Hora';

  @override
  String get scheduleDelete => 'Eliminar programación';

  @override
  String get schedulePaused => 'En pausa';

  @override
  String get scheduleFinished => 'Sin más ejecuciones';

  @override
  String get scheduleDigest => 'Resumen matutino';

  @override
  String get scheduleEmpty => 'Aún no hay mensajes programados';

  @override
  String get scheduleUnsupported =>
      'Los mensajes programados no están disponibles para esta conexión.';

  @override
  String get scheduleSaveFailed =>
      'No se pudo guardar este cambio. Inténtalo de nuevo.';

  @override
  String get scheduleNeedsYou =>
      'Abre la conversación para responder a una solicitud.';

  @override
  String get scheduleBusy => 'La conversación ya estaba trabajando.';

  @override
  String get scheduleUnavailable =>
      'No se pudo contactar con el servidor. Comprueba que esté en marcha y que este teléfono pueda llegar a él.';

  @override
  String get scheduleSignInAgain =>
      'El servidor necesita de nuevo su contraseña. Abre el servidor e inicia sesión antes de la próxima ejecución.';

  @override
  String get scheduleAllowHttp =>
      'Este servidor usa HTTP sin cifrar y aún no está permitido. Abre el servidor y permite HTTP antes de la próxima ejecución.';

  @override
  String get scheduleAgentFailed =>
      'El agente se detuvo con un error. Abre la conversación para ver qué pasó.';

  @override
  String get scheduleCertificateChanged =>
      'El certificado del servidor cambió. Abre el servidor para comprobarlo antes de la próxima ejecución.';

  @override
  String get scheduleCertificateInvalid =>
      'El certificado del servidor está caducado o aún no es válido. Abre el servidor para comprobarlo antes de la próxima ejecución.';

  @override
  String get scheduleTargetChanged =>
      'La conexión o el destino guardados cambiaron. Edita esta programación.';

  @override
  String get scheduleInterrupted =>
      'La ejecución se interrumpió. No se volvió a enviar.';

  @override
  String get scheduleTimeout =>
      'La ejecución alcanzó su límite de tiempo. Revisa la conversación.';

  @override
  String get scheduleUncertain =>
      'No se pudo confirmar la entrega. Revisa la conversación antes de enviar de nuevo.';

  @override
  String get scheduleMissed =>
      'Omitida según tu elección para ejecuciones perdidas.';

  @override
  String get scheduleDeleteBody =>
      'Se quitarán las ejecuciones futuras y el historial de esta programación. Los mensajes ya enviados permanecen en sus conversaciones.';

  @override
  String scheduleToday(String time) {
    return 'Hoy, $time';
  }

  @override
  String scheduleTomorrow(String time) {
    return 'Mañana, $time';
  }

  @override
  String scheduleInMinutes(String minutes) {
    return 'en $minutes min';
  }

  @override
  String scheduleDeleteNamedTitle(String name) {
    return '¿Eliminar “$name”?';
  }

  @override
  String get openCode2MoveProgress =>
      'Llevando tus conversaciones a OpenCode 2…';

  @override
  String openCode2MoveRetrying(int retry, int total) {
    return 'El teléfono aún se está preparando. Reintenta en breve (intento $retry de $total).';
  }

  @override
  String get openCode2MoveFailedNotReady =>
      'El servidor en este teléfono no estaba listo. Tus conversaciones y archivos de OpenCode 1 siguen en este teléfono, sin tocar. Reintenta en un minuto.';

  @override
  String get openCode2MoveFailedSeparateHistory =>
      'OpenCode 2 en este teléfono ya tiene sus propias conversaciones, así que la mudanza se detuvo para proteger ambas. No se cambió nada.';

  @override
  String get openCode2MoveFailedStopped =>
      'La mudanza se detuvo antes de terminar, a menudo porque el teléfono está corto de espacio libre o el servidor está ocupado. Tus conversaciones y archivos de OpenCode 1 siguen en este teléfono, sin tocar.';

  @override
  String get openCode2MoveFailedNotConfirmed =>
      'OpenCode 2 no confirmó que tus conversaciones llegaran. Tus conversaciones y archivos de OpenCode 1 siguen en este teléfono, sin tocar.';

  @override
  String get terminalLinkTitle => 'Enlace en la terminal';

  @override
  String get terminalLinkOpen => 'Abrir el enlace';

  @override
  String get terminalLinkCopy => 'Copiar el enlace';

  @override
  String get terminalCopiedToClipboard => 'Copiado al portapapeles';

  @override
  String get chatRequestAskOutsideWrite => '¿Escribir fuera de este proyecto?';

  @override
  String get chatRequestAskOutsideRead => '¿Leer fuera de este proyecto?';

  @override
  String get chatRequestAskOutsideRun =>
      '¿Correr un comando fuera de este proyecto?';

  @override
  String get phoneSetupStageRetrying => 'Se interrumpió. Reintentando';

  @override
  String get linuxMgrRow => 'Linux';

  @override
  String get linuxMgrRowDetail =>
      'Qué corre, puertos, almacenamiento y paquetes del Ubuntu dentro de esta app';

  @override
  String get linuxMgrTitle => 'Linux en este teléfono';

  @override
  String get linuxMgrRunning => 'En ejecución';

  @override
  String get linuxMgrRunningDetail =>
      'Qué corre dentro de Linux, y cómo detenerlo';

  @override
  String get linuxMgrPorts => 'Puertos';

  @override
  String get linuxMgrPortsDetail =>
      'Qué está escuchando, y cómo previsualizarlo';

  @override
  String get linuxMgrStorage => 'Almacenamiento';

  @override
  String get linuxMgrStorageDetail =>
      'A dónde va el espacio, y cómo limpiar cachés';

  @override
  String get linuxMgrPackages => 'Paquetes';

  @override
  String get linuxMgrPackagesDetail =>
      'Software instalado: instalar, quitar y actualizar';

  @override
  String get linuxMgrRepair => 'Reparar Linux';

  @override
  String get linuxMgrRepairDetail =>
      'Revisa Linux, OpenCode y tus herramientas, y arregla lo roto. Tus proyectos no se tocan.';

  @override
  String get linuxMgrRepairTitle => '¿Reparar Linux?';

  @override
  String get linuxMgrRepairBody =>
      'Revisa Linux, OpenCode y las herramientas que agregaste, e instala otra vez lo que falte o esté roto. Necesita conexión y puede tomar varios minutos.';

  @override
  String get linuxMgrRepairKept =>
      'Tus proyectos e inicios de sesión no se tocan';

  @override
  String get linuxMgrReset => 'Reiniciar Linux';

  @override
  String get linuxMgrResetDetail =>
      'Empezar otra vez desde un Ubuntu fresco. Tus proyectos se quedan.';

  @override
  String get linuxMgrResetTitle => '¿Reiniciar Linux?';

  @override
  String get linuxMgrResetBody =>
      'Linux se borra y se configura de nuevo desde cero. Esto no se puede deshacer.';

  @override
  String get linuxMgrResetLostTools =>
      'Paquetes y herramientas instalados: OpenCode, Python, AI Team, dictado por voz';

  @override
  String get linuxMgrResetLostSignIns =>
      'Inicios de sesión de agentes y sus ajustes';

  @override
  String get linuxMgrResetLostChats =>
      'Conversaciones conservadas por el servidor en este teléfono';

  @override
  String get linuxMgrResetKept => 'Tus proyectos se quedan en el teléfono';

  @override
  String get linuxMgrResetWord => 'Reiniciar';

  @override
  String get linuxMgrProblemBusy =>
      'Otra tarea está usando Linux ahora mismo. Espera un momento y reintenta.';

  @override
  String get linuxMgrProblemSetup =>
      'La configuración está corriendo. Espera a que termine.';

  @override
  String get linuxMgrProblemJob =>
      'Una tarea de paquetes está corriendo. Espera a que termine.';

  @override
  String get linuxMgrProblemUnavailable =>
      'Linux no está configurado o no responde. Abre En este teléfono para configurarlo.';

  @override
  String get linuxMgrProblemFailed => 'Eso no terminó. Reintenta.';

  @override
  String get linuxMgrLoadFailedTitle => 'No se pudo leer Linux';

  @override
  String get linuxMgrProcTitle => 'Corriendo en Linux';

  @override
  String linuxMgrProcSummary(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count programas',
      one: '1 programa',
    );
    return '$_temp0 · $memory';
  }

  @override
  String get linuxMgrKindServer => 'Servidor de OpenCode';

  @override
  String get linuxMgrKindPaseo => 'Paseo';

  @override
  String get linuxMgrKindAgent => 'Agente';

  @override
  String get linuxMgrKindDev => 'Servidor dev';

  @override
  String get linuxMgrKindOther => 'Otro';

  @override
  String get linuxMgrAboutServer =>
      'El servidor de OpenCode con el que esta app habla. Detenlo desde En este teléfono, no desde aquí.';

  @override
  String get linuxMgrAboutPaseo =>
      'Paseo corre agentes de código en este teléfono. Detenerlo termina los agentes que arrancó.';

  @override
  String get linuxMgrAboutAgent =>
      'Un agente de código o parte del AI Team. Detenerlo termina el trabajo que tiene en marcha.';

  @override
  String get linuxMgrAboutDev =>
      'Un programa que sirve a tu proyecto mientras lo construyes. Inícialo otra vez desde el proyecto cuando lo necesites.';

  @override
  String get linuxMgrAboutOther =>
      'Un programa corriendo dentro de Linux. Detenerlo lo termina; nada lo vuelve a arrancar.';

  @override
  String get linuxMgrProtected => 'Gestionado desde En este teléfono';

  @override
  String linuxMgrStopTitle(String name) {
    return '¿Detener $name?';
  }

  @override
  String get linuxMgrStopBody =>
      'Termina al instante y nada lo vuelve a arrancar. El trabajo sin guardar en él se pierde.';

  @override
  String linuxMgrStopHelpers(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count programas que arrancó se detienen con él',
      one: '1 programa que arrancó se detiene con él',
    );
    return '$_temp0';
  }

  @override
  String linuxMgrStopLabel(String name) {
    return 'Detener $name';
  }

  @override
  String get linuxMgrStopping => 'Deteniendo…';

  @override
  String linuxMgrStopped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se detuvieron $count programas',
      one: 'Se detuvo 1 programa',
    );
    return '$_temp0';
  }

  @override
  String linuxMgrStopForced(int count) {
    return '$count tuvieron que ser forzados';
  }

  @override
  String linuxMgrStopRemaining(int count) {
    return '$count no se quisieron detener';
  }

  @override
  String linuxMgrStopChanged(int count) {
    return '$count ya habían cambiado y se les dejó quietos';
  }

  @override
  String get linuxMgrNotStoppedTitle => 'No todo se detuvo';

  @override
  String get linuxMgrProcEmpty => 'Nada corre en Linux';

  @override
  String get linuxMgrProcEmptyBody =>
      'OpenCode y tus herramientas arrancan cuando los usas.';

  @override
  String get linuxMgrCopyCommand => 'Copiar el comando';

  @override
  String get linuxMgrPortsTitle => 'Puertos en Linux';

  @override
  String linuxMgrPortRow(int port) {
    return 'Puerto $port';
  }

  @override
  String linuxMgrPortUsedBy(String name) {
    return 'Usado por $name';
  }

  @override
  String get linuxMgrPortUnknown => 'Programa desconocido';

  @override
  String get linuxMgrPortLocal => 'Solo en este teléfono';

  @override
  String get linuxMgrPortAll => 'Abierto a otros dispositivos de tu red';

  @override
  String get linuxMgrPortNotPage => 'No es una página web';

  @override
  String get linuxMgrPortPreview => 'Abrir la vista previa';

  @override
  String linuxMgrPortPreviewNamed(int port) {
    return 'Abrir la vista previa del puerto $port';
  }

  @override
  String get linuxMgrPortCopy => 'Copiar la dirección';

  @override
  String get linuxMgrPortsNote =>
      'Solo se listan los puertos que este teléfono puede abrir.';

  @override
  String get linuxMgrPortsEmpty => 'Nada está escuchando';

  @override
  String get linuxMgrPortsEmptyBody =>
      'Cuando arranca un servidor dev, su puerto aparece aquí.';

  @override
  String get linuxMgrStorageTitle => 'Almacenamiento de Linux';

  @override
  String linuxMgrStorageTotal(String size) {
    return '$size usados dentro de Linux';
  }

  @override
  String get linuxMgrCatBase => 'Linux mismo';

  @override
  String get linuxMgrCatBaseDetail => 'Ubuntu y sus archivos de sistema';

  @override
  String get linuxMgrCatPackages => 'Descargas de paquetes';

  @override
  String get linuxMgrCatPackagesDetail =>
      'Paquetes descargados y listas de paquetes';

  @override
  String get linuxMgrCatTools => 'Node y cachés';

  @override
  String get linuxMgrCatToolsDetail => 'Node, npm y descargas de configuración';

  @override
  String get linuxMgrCatAgents => 'Agentes y OpenCode';

  @override
  String get linuxMgrCatAgentsDetail =>
      'OpenCode, Paseo, Claude Code, AI Team y sus ajustes guardados';

  @override
  String get linuxMgrCatProjects => 'Proyectos';

  @override
  String get linuxMgrCatProjectsDetail =>
      'Tu trabajo. Nunca se limpia desde aquí.';

  @override
  String get linuxMgrCatTemp => 'Archivos temporales y logs';

  @override
  String get linuxMgrCatTempDetail => 'Archivos de trabajo y logs de programas';

  @override
  String get linuxMgrFreeUp => 'Liberar espacio';

  @override
  String get linuxMgrCleanApt => 'Limpiar las descargas de paquetes';

  @override
  String get linuxMgrCleanAptDetail =>
      'Linux los descarga otra vez si los necesita';

  @override
  String get linuxMgrCleanTools => 'Limpiar Node y las cachés de descargas';

  @override
  String get linuxMgrCleanToolsDetail =>
      'descargas de npm y de configuración, traídas de nuevo cuando hagan falta';

  @override
  String get linuxMgrCleanTemp =>
      'Limpiar los archivos temporales y logs viejos';

  @override
  String get linuxMgrCleanTempDetail =>
      'Archivos sin tocar por un día y logs de más de una semana';

  @override
  String get linuxMgrCleanAgents => 'Quitar las versiones viejas de agentes';

  @override
  String get linuxMgrCleanAgentsDetail =>
      'Versiones de Paseo que ya no se usan';

  @override
  String linuxMgrCleanBody(String size) {
    return 'Vuelven cerca de $size. Esto no se puede deshacer, y nada que hayas hecho se toca.';
  }

  @override
  String get linuxMgrCleanKept =>
      'Los proyectos, inicios de sesión y ajustes se quedan';

  @override
  String linuxMgrCleanDone(String size) {
    return 'Se liberó $size';
  }

  @override
  String get linuxMgrCleanNothing => 'No había nada por limpiar';

  @override
  String get linuxMgrCleanNone => 'No hay nada por limpiar ahora mismo';

  @override
  String get linuxMgrMeasuring => 'Midiendo…';

  @override
  String get linuxMgrPkgTitle => 'Paquetes';

  @override
  String linuxMgrPkgCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count paquetes instalados',
      one: '1 paquete instalado',
    );
    return '$_temp0';
  }

  @override
  String get linuxMgrPkgSearch => 'Buscar los paquetes instalados';

  @override
  String get linuxMgrPkgNoMatch => 'Ningún paquete instalado coincide con eso';

  @override
  String linuxMgrPkgShowAll(int count) {
    return 'Mostrar los $count paquetes';
  }

  @override
  String get linuxMgrPkgUpdate => 'Actualizar el sistema';

  @override
  String get linuxMgrPkgUpdateDetail =>
      'Refresca las listas de paquetes e instala las actualizaciones disponibles';

  @override
  String get linuxMgrPkgUpdateTitle => '¿Actualizar los paquetes de Linux?';

  @override
  String get linuxMgrPkgUpdateBody =>
      'Descarga las últimas actualizaciones y las instala. Usa tu conexión, puede tomar varios minutos y se detiene por sí solo a los 45 minutos.';

  @override
  String get linuxMgrPkgInstall => 'Instalar un paquete';

  @override
  String get linuxMgrPkgInstallDetail => 'Por su nombre, por ejemplo htop';

  @override
  String get linuxMgrPkgName => 'Nombre del paquete';

  @override
  String get linuxMgrPkgNameHelper =>
      'Nombres de paquetes de Ubuntu, separados por espacios';

  @override
  String get linuxMgrPkgNameInvalid =>
      'Usa solo letras minúsculas, dígitos y + - . en cada nombre';

  @override
  String get linuxMgrPkgCheck => 'Verificar';

  @override
  String linuxMgrPkgAlready(String name) {
    return '$name ya está instalado';
  }

  @override
  String linuxMgrPkgUnknown(String name) {
    return 'No hay ningún paquete llamado $name. Actualizar el sistema refresca la lista de paquetes.';
  }

  @override
  String linuxMgrPkgInstallTitle(String names) {
    return '¿Instalar $names?';
  }

  @override
  String get linuxMgrPkgInstallBody =>
      'Se descarga de Ubuntu y se instala. Usa tu conexión.';

  @override
  String linuxMgrPkgInstallLabel(String names) {
    return 'Instalar $names';
  }

  @override
  String linuxMgrPkgRemove(String name) {
    return 'Quitar $name';
  }

  @override
  String linuxMgrPkgRemoveTitle(String name) {
    return '¿Quitar $name?';
  }

  @override
  String get linuxMgrPkgRemoveBody =>
      'Se quita de Linux. Tus proyectos no se tocan.';

  @override
  String linuxMgrPkgAlsoRemoved(String list) {
    return 'También se quita: $list';
  }

  @override
  String get linuxMgrPkgLocked =>
      'Linux o tus herramientas lo necesitan. Las herramientas se quitan desde En este teléfono.';

  @override
  String linuxMgrPkgBlockedPlan(String list) {
    return 'Quitarlo también quitaría $list, que Linux necesita.';
  }

  @override
  String get linuxMgrPkgChecking => 'Revisando qué quitaría…';

  @override
  String linuxMgrJobInstalling(String names) {
    return 'Instalando $names';
  }

  @override
  String linuxMgrJobRemoving(String names) {
    return 'Quitando $names';
  }

  @override
  String get linuxMgrJobUpdating => 'Actualizando los paquetes de Linux';

  @override
  String get linuxMgrJobKeepsGoing =>
      'Sigue corriendo si sales de esta página.';

  @override
  String linuxMgrJobStopInstall(String names) {
    return 'Detener la instalación de $names';
  }

  @override
  String linuxMgrJobStopRemove(String names) {
    return 'Detener la eliminación de $names';
  }

  @override
  String get linuxMgrJobStopUpdate => 'Detener la actualización';

  @override
  String get linuxMgrJobStopBody =>
      'Se detiene ahora. Un paquete a medio terminar se repara la próxima vez que corras una tarea de paquetes.';

  @override
  String linuxMgrJobDoneInstall(String names) {
    return 'Se instalaron $names';
  }

  @override
  String linuxMgrJobDoneRemove(String names) {
    return 'Se quitaron $names';
  }

  @override
  String get linuxMgrJobDoneUpdate => 'Los paquetes de Linux están al día';

  @override
  String get linuxMgrJobFailed =>
      'Eso no terminó. Nada que hayas hecho se tocó. Reintenta; continúa donde se quedó.';

  @override
  String get linuxMgrJobTimedOut =>
      'Duró más de 45 minutos y se detuvo solo. Corre otra vez para continuar.';

  @override
  String get linuxMgrJobInterrupted =>
      'Se detuvo antes de terminar, por ejemplo cuando se cerró la app. Corre otra vez para continuar.';

  @override
  String get linuxMgrJobNoticeInstall => 'Instalando paquetes de Linux';

  @override
  String get linuxMgrJobNoticeRemove => 'Quitando paquetes de Linux';

  @override
  String get linuxMgrJobNoticeUpdate => 'Actualizando paquetes de Linux';

  @override
  String get linuxMgrJobDismiss => 'Listo';

  @override
  String linuxMgrJobStopInstallTitle(String names) {
    return '¿Detener la instalación de $names?';
  }

  @override
  String linuxMgrJobStopRemoveTitle(String names) {
    return '¿Detener la eliminación de $names?';
  }

  @override
  String get linuxMgrJobStopUpdateTitle =>
      '¿Detener la actualización de los paquetes de Linux?';

  @override
  String get linuxMgrCleanAptAsk => '¿Limpiar las descargas de paquetes?';

  @override
  String get linuxMgrCleanToolsAsk =>
      '¿Limpiar Node y las cachés de descargas?';

  @override
  String get linuxMgrCleanTempAsk =>
      '¿Limpiar los archivos temporales y logs viejos?';

  @override
  String get linuxMgrCleanAgentsAsk =>
      '¿Quitar las versiones viejas de agentes?';

  @override
  String get kitLogSearch => 'Buscar en esta salida';

  @override
  String get kitLogPause => 'Pausar el seguimiento';

  @override
  String get kitLogFollow => 'Seguir la salida nueva';

  @override
  String get kitLogPreviousMatch => 'Coincidencia anterior';

  @override
  String get kitLogNextMatch => 'Coincidencia siguiente';

  @override
  String get servicesAlsoRunning => 'También corriendo';

  @override
  String get servicesAlsoPhoneHint =>
      'Encontrados por los puertos que escuchan, sin importar cómo se arrancaron.';

  @override
  String get servicesAlsoServerHint =>
      'Este servidor lista los comandos que corre. Un servidor arrancado en una terminal suya no aparece aquí.';

  @override
  String servicesPorts(int count, String ports) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Puertos $ports',
      one: 'Puerto $ports',
    );
    return '$_temp0';
  }

  @override
  String get servicesNoPortYet => 'Aún no se encontró ningún puerto';

  @override
  String get servicesOpenPreview => 'Abrir la vista previa';

  @override
  String servicesOpenPreviewNamed(String name) {
    return 'Previsualizar $name';
  }

  @override
  String servicesPreviewPort(String port) {
    return 'Previsualizar el puerto $port';
  }

  @override
  String get servicesSaveCommand => 'Guardar como comando';

  @override
  String servicesStopFoundTitle(String name) {
    return '¿Detener $name?';
  }

  @override
  String get servicesStopFoundHint =>
      'Esto termina el programa en la máquina donde corre. Lo que no haya guardado se pierde.';

  @override
  String get servicesLogsUnavailable =>
      'Este programa no se arrancó desde esta app, así que su salida no se conserva donde la app pueda leerla. Elige Guardar como comando en su fila, detén este y arranca el comando guardado aquí para ver sus logs.';

  @override
  String devPreviewRunningNow(String name) {
    return 'Corriendo ahora: $name';
  }

  @override
  String get devPreviewNoneFound => 'No se encontraron apps corriendo';

  @override
  String get linuxMgrResetNotErasedTitle => 'Linux no se reinició';

  @override
  String get linuxMgrResetNotErasedBody =>
      'No se borró nada. Cierra las terminales y detén lo que corre en Linux, y luego reintenta.';

  @override
  String get linuxMgrResetDetailsLabel => 'Código del error';

  @override
  String get linuxMgrResetCleanupTitle => 'Configurando Linux de nuevo';

  @override
  String linuxMgrResetForgetFailed(String name) {
    return '“$name” de antes del reinicio no se pudo olvidar y sigue guardado.';
  }

  @override
  String linuxMgrResetLeft(String name, String what) {
    return 'De antes del reinicio, “$name” conserva guardado: $what.';
  }

  @override
  String get linuxMgrResetLeftConnection => 'su conexión guardada';

  @override
  String get servicesAlsoPhoneFailed =>
      'No se pudo revisar qué más corre en Linux ahora mismo. Se revisa otra vez en unos segundos.';

  @override
  String linuxMgrProcGroupCount(int count) {
    return '$count procesos';
  }

  @override
  String get gitRepairFailureWords => 'El historial de Git no se puede leer.';

  @override
  String get gitRepairAction => 'Reparar';

  @override
  String get gitRepairTitle => 'Reparar el historial de Git';

  @override
  String gitRepairWorking(String project) {
    return 'Reparando $project…';
  }

  @override
  String get gitRepairLooking => 'Buscando los archivos de Git que faltan…';

  @override
  String gitRepairRestoring(int done, int total) {
    return 'Restituyendo $done de $total archivos de Git…';
  }

  @override
  String get gitRepairChecking => 'Revisando el proyecto con Git…';

  @override
  String gitRepairDoneTitle(String project) {
    return '$project quedó reparado';
  }

  @override
  String gitRepairDoneBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count archivos de Git volvieron, y Git puede leer el proyecto otra vez.',
      one: '1 archivo de Git volvió, y Git puede leer el proyecto otra vez.',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairHealthyTitle => 'No faltaba nada';

  @override
  String gitRepairHealthyBody(String project) {
    return 'Git ya puede leer $project.';
  }

  @override
  String get gitRepairStart => 'Empezar la conversación';

  @override
  String gitRepairCannotTitle(String project) {
    return 'No se puede reparar $project';
  }

  @override
  String gitRepairMissingBody(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other:
          '$count archivos de Git ya no están en este teléfono. Clona el proyecto otra vez desde donde está alojado, o restaura una copia.',
      one:
          '1 archivo de Git ya no está en este teléfono. Clona el proyecto otra vez desde donde está alojado, o restaura una copia.',
    );
    return '$_temp0';
  }

  @override
  String get gitRepairStillBrokenBody =>
      'Git aún no puede leerlo. Clona el proyecto otra vez desde donde está alojado, o restaura una copia.';

  @override
  String get gitRepairNotRepositoryTitle => 'No se puede reparar esta carpeta';

  @override
  String get gitRepairNotRepositoryBody =>
      'Solo los proyectos Git en la carpeta de proyectos de En este teléfono se pueden reparar aquí.';

  @override
  String get gitRepairClose => 'Cerrar';

  @override
  String get gitProblemNotARepository =>
      'Esta carpeta no es un repositorio Git.';

  @override
  String get gitProblemGitMissing =>
      'Git no está instalado donde vive este proyecto. Instálalo allí y reintenta.';

  @override
  String get gitProblemFolderMissing =>
      'No se encontró la carpeta del proyecto. Quizá se movió o se eliminó.';

  @override
  String get gitProblemEnvironmentUnavailable =>
      'No se pudo alcanzar el lugar donde vive este proyecto. Revisa que esté corriendo y reintenta.';

  @override
  String get gitProblemServerCannotRunCommands =>
      'Este servidor no puede correr comandos de Git para la app.';

  @override
  String get gitProblemOutputTooLarge =>
      'Hay demasiados cambios por leer aquí. Haz commit o limpia algunos archivos y reintenta.';

  @override
  String get gitProblemTimedOut =>
      'Git tardó demasiado en responder. Reintenta.';

  @override
  String get gitProblemUnsafeRepository =>
      'Git no funciona en esta carpeta porque otro usuario es su dueño. Arregla el dueño de la carpeta en ese dispositivo.';

  @override
  String get gitProblemRepositoryLocked =>
      'Otro comando de Git está usando este repositorio. Espera a que termine y reintenta.';

  @override
  String get gitProblemNoUpstream =>
      'Esta rama aún no está vinculada a una rama del remoto. Empújala primero.';

  @override
  String get gitProblemNoRemote =>
      'Este repositorio no tiene remoto al que empujar. Agrega uno en ese dispositivo primero.';

  @override
  String get gitProblemDetachedHead =>
      'No estás sobre una rama. Cámbiate a una primero.';

  @override
  String get gitProblemCannotFastForward =>
      'El remoto y esta rama tienen ambos commits nuevos, así que no se pueden combinar automáticamente. Haz merge o rebase en ese dispositivo.';

  @override
  String get gitProblemPushRejected =>
      'El remoto tiene commits que esta rama no tiene. Trae primero, y luego empuja otra vez.';

  @override
  String get gitProblemLocalChangesBlock =>
      'Tus cambios sin commit se sobrescribirían. Haz commit o descártalos primero.';

  @override
  String get gitProblemAuthRequired =>
      'El remoto necesita que inicies sesión. Configura el inicio de sesión de Git donde vive este proyecto y reintenta.';

  @override
  String get gitProblemAuthRequiredSignIn =>
      'El remoto necesita que inicies sesión. Inicia sesión en GitHub y reintenta.';

  @override
  String get gitProblemGitHubTokenRejected =>
      'GitHub no aceptó tu inicio de sesión. Inicia sesión en GitHub otra vez.';

  @override
  String get gitProblemNoPermission =>
      'Tu cuenta no puede empujar a este repositorio. Pide acceso a su dueño.';

  @override
  String get gitProblemNetwork =>
      'No se pudo alcanzar el remoto. Revisa la conexión a internet y reintenta.';

  @override
  String get gitProblemRemoteNotFound =>
      'No se encontró el repositorio remoto, o tu cuenta no puede verlo.';

  @override
  String get gitProblemNothingSelected => 'Marca al menos un archivo.';

  @override
  String get gitProblemEmptyMessage => 'Describe primero qué cambió.';

  @override
  String get gitProblemNothingToCommit =>
      'No hay nada por hacer commit en los archivos que elegiste.';

  @override
  String get gitProblemIdentityMissing =>
      'Git necesita tu nombre y tu correo para hacer un commit. Agrégalos y reintenta.';

  @override
  String get gitProblemSigningFailed =>
      'Git no pudo firmar el commit. Revisa la firma de commits en ese dispositivo.';

  @override
  String get gitProblemHookRejected =>
      'Una verificación de este repositorio detuvo la acción. Detalles muestra lo que dijo.';

  @override
  String get gitProblemInvalidBranchName =>
      'Ese no es un nombre de rama válido. Evita los espacios y caracteres como ~ ^ : ? * [';

  @override
  String get gitProblemBranchExists => 'Ya existe una rama con ese nombre.';

  @override
  String get gitProblemBranchNotFound => 'No se encontró esa rama.';

  @override
  String get gitProblemConflicts =>
      'Algunos archivos tienen conflictos. Resuélvelos en ese dispositivo primero.';

  @override
  String get gitProblemOperationInProgress =>
      'Un merge o rebase no terminó. Termínalo o cancélalo en ese dispositivo primero.';

  @override
  String get gitProblemCannotDiscardConflict =>
      'Los archivos con conflictos no se pueden descartar aquí.';

  @override
  String get gitProblemUnknown =>
      'Git no pudo terminar. Detalles muestra lo que dijo.';

  @override
  String get gitUnavailableNotConnected =>
      'Conéctate a un servidor para ver sus cambios de Git.';

  @override
  String get gitUnavailableNoProject =>
      'Abre un proyecto para ver sus cambios de Git.';

  @override
  String get gitUnavailableServerCannotRun =>
      'Este servidor no puede correr Git para la app. Usa Git en esa computadora en su lugar.';

  @override
  String get gitNotRepositoryTitle => 'No es un repositorio Git';

  @override
  String get gitNotRepositoryBody => 'Esta carpeta de proyecto aún no usa Git.';

  @override
  String get gitCleanBody => 'Todo está con commit.';

  @override
  String get gitDetachedHead => 'No estás sobre una rama';

  @override
  String get gitNoCommitsYet => 'Aún no hay commits';

  @override
  String gitAhead(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count commits por empujar',
      one: '1 commit por empujar',
    );
    return '$_temp0';
  }

  @override
  String get gitOperationMerge => 'Merge sin terminar';

  @override
  String get gitOperationRebase => 'Rebase sin terminar';

  @override
  String get gitOperationCherryPick => 'Cherry-pick sin terminar';

  @override
  String get gitOperationRevert => 'Revert sin terminar';

  @override
  String get gitUsesOwnSignIn =>
      'Enviar y traer lo último usan el inicio de sesión de Git ya configurado donde vive este proyecto.';

  @override
  String get gitChangeAdded => 'Agregado';

  @override
  String get gitChangeModified => 'Modificado';

  @override
  String get gitChangeDeleted => 'Eliminado';

  @override
  String get gitChangeRenamed => 'Renombrado';

  @override
  String get gitChangeCopied => 'Copiado';

  @override
  String get gitChangeTypeChanged => 'Tipo cambiado';

  @override
  String get gitChangeUntracked => 'Nuevo';

  @override
  String get gitChangeConflicted => 'Conflicto';

  @override
  String get gitActionPull => 'Traer';

  @override
  String get gitActionPush => 'Empujar';

  @override
  String get gitActionCommit => 'Hacer commit';

  @override
  String get gitActionSwitchBranch => 'Cambiar de rama';

  @override
  String get gitActionCreateBranch => 'Rama nueva';

  @override
  String get gitActionDiscard => 'Descartar los cambios';

  @override
  String get gitActionRefresh => 'Actualizar';

  @override
  String get gitActionSaveIdentity => 'Guardar el nombre y el correo';

  @override
  String get gitBusyPull => 'Trayendo lo último…';

  @override
  String get gitBusyPush => 'Empujando…';

  @override
  String get gitBusyCommit => 'Guardando…';

  @override
  String get gitBusySwitchBranch => 'Cambiando de rama…';

  @override
  String get gitBusyCreateBranch => 'Creando la rama…';

  @override
  String get gitBusyDiscard => 'Descartando los cambios…';

  @override
  String get gitBusyRefresh => 'Buscando cambios…';

  @override
  String get gitBusySaveIdentity => 'Guardando…';

  @override
  String get gitDoneUpToDate => 'Ya está al día.';

  @override
  String get gitDonePulled => 'Se trajeron los últimos cambios.';

  @override
  String get gitDonePushed => 'Empujado.';

  @override
  String get gitDoneSwitched => 'Se cambió de rama.';

  @override
  String get gitDoneCreated => 'Se creó la rama y se cambió a ella.';

  @override
  String get gitDoneDiscarded => 'Se descartaron los cambios.';

  @override
  String get gitDoneIdentitySaved =>
      'Se guardaron tu nombre y tu correo para este repositorio.';

  @override
  String get gitCommitMessageHint => 'Describe qué cambió';

  @override
  String get gitWriteItForMe => 'Escríbelo por mí';

  @override
  String get gitAskAgentFixConflicts =>
      'Pedirle al agente que arregle los conflictos';

  @override
  String gitConflictCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos tienen conflictos',
      one: '1 archivo tiene conflictos',
    );
    return '$_temp0';
  }

  @override
  String gitAskAgentFixConflictsPrompt(String files) {
    return 'Resuelve los conflictos de merge en $files. Conserva lo que ambas partes querían decir y cuéntame qué elegiste.';
  }

  @override
  String get gitBranchNameHint => 'Nombre de la rama';

  @override
  String get gitIdentityName => 'Nombre';

  @override
  String get gitIdentityEmail => 'Correo';

  @override
  String get gitHubSignIn => 'Iniciar sesión en GitHub';

  @override
  String get gitHubSignOut => 'Cerrar la sesión de GitHub';

  @override
  String get gitHubSignOutTitle => '¿Cerrar la sesión de GitHub?';

  @override
  String gitHubSignedInAs(String login) {
    return 'Con sesión iniciada en GitHub como $login';
  }

  @override
  String get gitHubRequestingCode =>
      'Consiguiendo un código de inicio de sesión…';

  @override
  String get gitHubEnterCode =>
      'Escribe este código en GitHub para terminar de iniciar sesión.';

  @override
  String get gitHubOpenGitHub => 'Abrir GitHub';

  @override
  String get gitHubCopyCode => 'Copiar el código';

  @override
  String get gitHubWaiting => 'Esperando a que termines en GitHub…';

  @override
  String get gitHubFinishing => 'Terminando el inicio de sesión…';

  @override
  String get gitHubCancel => 'Cancelar';

  @override
  String get gitHubSignOutBody =>
      'La app olvida tu inicio de sesión de GitHub en este dispositivo. Para quitar su acceso por completo, revócalo también en GitHub.';

  @override
  String get gitHubManageAccess => 'Gestionar el acceso en GitHub';

  @override
  String get gitHubPushOverHttps =>
      'Empujar a GitHub usa este inicio de sesión por HTTPS. Tus ajustes de SSH no cambian.';

  @override
  String get gitHubProblemExpired =>
      'El código venció antes de escribirse. Inicia sesión otra vez para conseguir uno nuevo.';

  @override
  String get gitHubProblemDenied => 'El inicio de sesión se canceló en GitHub.';

  @override
  String get gitHubProblemDisabled =>
      'El inicio de sesión en GitHub aún no está activado para esta app.';

  @override
  String get gitHubProblemNetwork =>
      'No se pudo alcanzar GitHub. Revisa la conexión a internet y reintenta.';

  @override
  String get gitHubProblemTokenRejected =>
      'Tu inicio de sesión de GitHub terminó. Inicia sesión otra vez.';

  @override
  String get gitHubProblemUnexpected =>
      'GitHub mandó una respuesta que la app no entendió. Reintenta.';

  @override
  String get gitProblemInvalidRemoteName =>
      'Ese no es un nombre de remoto válido. Usa letras, números, puntos, guiones o guiones bajos.';

  @override
  String get gitProblemInvalidRemoteUrl =>
      'Esa dirección no parece correcta. Usa una como https://github.com/owner/name.';

  @override
  String get gitProblemRemoteAddressHasPassword =>
      'Saca la contraseña o el token de la dirección. Mejor inicia sesión en GitHub, así no se guarda ningún secreto en el repositorio.';

  @override
  String get gitProblemRemoteExists =>
      'Ya está configurado un remoto con ese nombre. Elige otro nombre.';

  @override
  String get gitProblemInvalidGitHubRepositoryName =>
      'Usa un nombre de repositorio de hasta 100 letras, números, puntos, guiones o guiones bajos.';

  @override
  String get gitProblemGitHubRepositoryNameUnavailable =>
      'Ese nombre ya se usa o no está disponible en tu cuenta de GitHub. Prueba otro.';

  @override
  String get gitProblemInvalidFolderName =>
      'Ese no es un nombre de carpeta utilizable. Usa letras, números, puntos, guiones o guiones bajos.';

  @override
  String get gitProblemFolderExists =>
      'Ya hay una carpeta con ese nombre. Elige otro nombre.';

  @override
  String get gitActionAddRemote => 'Agregar un remoto';

  @override
  String get gitActionClone => 'Clonar';

  @override
  String get gitActionInitialize => 'Empezar a registrar';

  @override
  String get gitActionPublish => 'Crear en GitHub';

  @override
  String get gitBusyAddRemote => 'Agregando el remoto…';

  @override
  String get gitBusyClone => 'Clonando…';

  @override
  String get gitBusyInitialize => 'Empezando Git en este proyecto…';

  @override
  String get gitBusyPublish => 'Creando el repositorio en GitHub…';

  @override
  String get gitDoneRemoteAdded => 'Se agregó el remoto.';

  @override
  String get gitDoneInitialized => 'Git está listo en este proyecto.';

  @override
  String get gitDoneGitHubRepositoryCreated =>
      'Se creó y se conectó el repositorio de GitHub. Tu proyecto aún no se envió.';

  @override
  String gitDoneCloned(String path) {
    return 'Se clonó en $path. Ábrelo desde tus proyectos.';
  }

  @override
  String get gitSheetTitle => 'Git';

  @override
  String gitSheetChipSemantics(String branch) {
    return 'Git, rama $branch. Abre las acciones de Git.';
  }

  @override
  String get gitSheetChipSemanticsPlain => 'Git. Abre las acciones de Git.';

  @override
  String get gitGetLatest => 'Traer lo último';

  @override
  String get gitSend => 'Enviar';

  @override
  String get gitSaveChanges => 'Guardar los cambios';

  @override
  String gitSaveChangesCount(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count archivos cambiados',
      one: '1 archivo cambiado',
      zero: 'Sin archivos cambiados',
    );
    return '$_temp0';
  }

  @override
  String get gitSaveFilesLabel => 'Archivos por incluir';

  @override
  String gitSaveButton(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Guardar $count archivos',
      one: 'Guardar 1 archivo',
    );
    return '$_temp0';
  }

  @override
  String gitSaveOnBranch(String branch) {
    return 'En $branch';
  }

  @override
  String get gitSwitchRow => 'Cambiar de rama';

  @override
  String gitSwitchRowOn(String branch) {
    return 'En $branch';
  }

  @override
  String gitBranchRemoteOnly(String remote) {
    return 'En $remote, aún no en este dispositivo';
  }

  @override
  String get gitBranchCurrent => 'Rama actual';

  @override
  String gitNewBranchFrom(String branch) {
    return 'Rama nueva desde $branch';
  }

  @override
  String get gitCloneRow => 'Clonar un repositorio';

  @override
  String get gitCloneRowHint => 'Copia un repositorio junto a este proyecto';

  @override
  String get gitCloneAddressLabel => 'Dirección del repositorio';

  @override
  String get gitCloneFolderLabel => 'Nombre de la carpeta';

  @override
  String gitCloneInto(String path) {
    return 'Se clona en $path';
  }

  @override
  String get gitCloneButton => 'Clonar';

  @override
  String get gitInitializeProject => 'Empezar a registrar este proyecto';

  @override
  String get gitInitializeProjectHint =>
      'Conserva estos archivos aquí y empieza a usar Git.';

  @override
  String get gitPublishToGitHub => 'Crear un repositorio de GitHub';

  @override
  String get gitPublishToGitHubHint => 'Elige un nombre y quién puede verlo.';

  @override
  String get gitPublishTitle => 'Crear el repositorio';

  @override
  String get gitPublishName => 'Nombre del repositorio';

  @override
  String get gitPublishNameHint =>
      'Usa letras, números, puntos, guiones o guiones bajos.';

  @override
  String get gitPublishVisibility => 'Quién puede verlo';

  @override
  String get gitPublishPrivate => 'Privado';

  @override
  String get gitPublishPublic => 'Público';

  @override
  String get gitPublishPrivateHint =>
      'Solo tú y las personas que elijas pueden ver este repositorio.';

  @override
  String get gitPublishPublicHint => 'Cualquiera puede ver este repositorio.';

  @override
  String get gitPublishButton => 'Crear el repositorio';

  @override
  String get gitRemotesRow => 'Remotos';

  @override
  String get gitRemotesRowNone => 'Aún ninguno';

  @override
  String get gitRemotesTitle => 'Remotos';

  @override
  String get gitRemotesEmpty =>
      'Este proyecto aún no tiene remoto. Agrega uno para enviar tu trabajo a algún lado.';

  @override
  String get gitRemoteAddTitle => 'Agregar un remoto';

  @override
  String get gitRemotePickGitHub => 'Elegir de GitHub';

  @override
  String get gitRemotePickGitHubTitle => 'Elegir un repositorio';

  @override
  String get gitRemotePickGitHubNone =>
      'Aún no hay repositorios en esta cuenta de GitHub.';

  @override
  String get gitRemoteRepoPrivate => 'Privado';

  @override
  String get gitRemoteNameLabel => 'Nombre';

  @override
  String get gitRemoteAddressLabel => 'Dirección';

  @override
  String get gitIdentityTitle => 'Tu nombre en los saves';

  @override
  String get gitHubSignedOutHint =>
      'Inicia sesión una vez con un código. No hay token que pegar.';

  @override
  String get gitHubSignInTitle => 'Inicio de sesión de GitHub';

  @override
  String get gitHubSignInWhy =>
      'Enviar a GitHub necesita tu inicio de sesión. Consigue un código aquí, escríbelo en GitHub, y la app guarda el inicio de sesión solo en este dispositivo.';

  @override
  String get gitHubSignOutConfirm => 'Cerrar la sesión';

  @override
  String gitUsesOwnSignInOn(String host) {
    return 'Enviar y traer lo último usan el inicio de sesión de Git configurado en $host.';
  }

  @override
  String gitDoneSaved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'los $count archivos',
      one: '1 archivo',
    );
    return 'Se guardó $_temp0.';
  }

  @override
  String get gitDoneSavedPlain => 'Guardado.';

  @override
  String get gitChecking => 'Revisando…';

  @override
  String get gitCheckFailed => 'No se pudo revisar; toca para reintentar';

  @override
  String gitNewCount(int count) {
    return '$count nuevos';
  }

  @override
  String get gitIdentityWhy =>
      'Los saves en este proyecto llevan este nombre y este correo, así que tu trabajo es tuyo cuando lo envías.';

  @override
  String get kitStepSliderLess => 'A';

  @override
  String get kitStepSliderMore => 'A';

  @override
  String get appearanceIntensityHelp =>
      'Tu conversación queda arriba y fácil de leer.';

  @override
  String get appearanceTextHelp =>
      'Aplica a toda la app, sobre el tamaño de texto de tu teléfono.';

  @override
  String kitCarouselPlace(int page, int count) {
    return '$page de $count';
  }

  @override
  String get gitPullNotOnRemote =>
      'Aún no está en el remoto. Empújalo primero.';

  @override
  String get gitPullNoRemote => 'Agrega primero un remoto abajo.';

  @override
  String get gitSendNoRemote => 'Aún no hay remoto. Agrega uno abajo.';

  @override
  String get chatSteerHint => 'Redireccionar mientras trabaja…';

  @override
  String chatSteerReadsAfterCommand(String agent) {
    return '$agent lee esto cuando termine el comando';
  }

  @override
  String chatSteerReadsAfterStep(String agent) {
    return '$agent lee esto cuando termine este paso';
  }

  @override
  String get chatSteerAgentFallback => 'El agente';

  @override
  String get chatSteerStopCommand => 'Detener el comando';

  @override
  String get chatSteerStopStep => 'Detener este paso';

  @override
  String get chatSendLaterOrRepeat => 'Enviar después o repetir';

  @override
  String get chatSendLaterSaved => 'Programado. Está en Mensajes programados.';

  @override
  String get composerSettingModel => 'Modelo';

  @override
  String get composerSettingPlan => 'Plan';

  @override
  String get composerSettingApprovalDetail =>
      'Permitir lo que pide sin preguntarte';

  @override
  String get chatAutoApproveOn => 'La autoaprobación está encendida';

  @override
  String get chatAutoApproveTurnOff => 'Apagar';

  @override
  String get chatNoModelSignedIn => 'Ningún modelo tiene sesión iniciada';

  @override
  String get chatNoModelSignIn => 'Iniciar sesión';

  @override
  String gitSendTo(String place, String branch) {
    return 'Enviar a $place · $branch';
  }

  @override
  String get gitPlaceGitHub => 'GitHub';

  @override
  String get gitStateUpToDate => 'Al día';

  @override
  String get gitStateNothingToSend => 'Nada por enviar';

  @override
  String get gitStateFirstSend => 'Primera vez, vincula esta rama';

  @override
  String get gitDetailsBranch => 'Rama';

  @override
  String get gitDetailsGetsFrom => 'Traer lo último de';

  @override
  String get gitDetailsSendsTo => 'Enviar a';

  @override
  String get gitDetailsAddress => 'Dirección del remoto';

  @override
  String gitDoneGotN(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count cambios nuevos',
      one: '1 cambio nuevo',
    );
    return 'Se consiguieron $_temp0.';
  }

  @override
  String reviewSummary(int files, int added, int removed) {
    String _temp0 = intl.Intl.pluralLogic(
      files,
      locale: localeName,
      other: '$files archivos cambiados',
      one: '1 archivo cambiado',
    );
    return '$_temp0 · +$added −$removed';
  }

  @override
  String get reviewAlreadyApplied =>
      'El agente ya hizo estos cambios en tu proyecto.';

  @override
  String get reviewLooksGood => 'Se ve bien';

  @override
  String get reviewUndoAll => 'Deshacer todo';

  @override
  String get reviewSendBackWithNote => 'Devolver con una nota';

  @override
  String reviewUndoTitle(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '¿Deshacer $count archivos cambiados?',
      one: '¿Deshacer 1 archivo cambiado?',
    );
    return '$_temp0';
  }

  @override
  String get reviewUndoBody =>
      'Tu proyecto vuelve a como estaba antes de que el agente empezara. La conversación se queda.';

  @override
  String get chatsNewBuildPrompt => '¿Qué quieres construir?';

  @override
  String get chatsNewProjectRow => 'Proyecto';

  @override
  String get chatsNewModelRow => 'Modelo';

  @override
  String get chatsNewSeparateCopyDetail =>
      'Trabaja en su propia copia, así que tus archivos se quedan como están.';

  @override
  String get modelPickerSheetTitle => 'Modelo';

  @override
  String modelPickerAllModels(int count) {
    return 'Todos los modelos ($count)';
  }

  @override
  String get modelPickerReasonDefault => 'Por defecto';

  @override
  String get modelPickerReasonRecent => 'Usado recientemente';

  @override
  String get modelPickerReasonCheapest => 'El más barato';

  @override
  String get modelPickerFree => 'Gratis';

  @override
  String get modelPickerScopeNewSessions => 'Para conversaciones nuevas';

  @override
  String get modelPickerScopeSession => 'Para esta conversación';

  @override
  String get searchRecentTitle => 'Recientes';

  @override
  String chatsHomeUnreachableShort(String servers) {
    return '$servers no responde';
  }

  @override
  String chatProjectChipFilesSemantics(String name) {
    return 'Proyecto $name. Abre sus archivos y herramientas.';
  }

  @override
  String chatProjectServerChipFilesSemantics(String name, String server) {
    return 'Proyecto $name en $server. Abre sus archivos y herramientas.';
  }

  @override
  String get chatTurnAllFiles => 'Todos los archivos';

  @override
  String chatTurnAllFilesMore(int count) {
    return 'Todos los archivos · $count más cambiaron';
  }

  @override
  String chatTurnFileLinesAdded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas agregadas',
      one: '1 línea agregada',
    );
    return '$_temp0';
  }

  @override
  String chatTurnFileLinesRemoved(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count líneas eliminadas',
      one: '1 línea eliminada',
    );
    return '$_temp0';
  }

  @override
  String get settingsHubSearch => 'Buscar en los ajustes';

  @override
  String get settingsHubServer => 'Servidor';

  @override
  String get settingsHubHelp => 'Ayuda';

  @override
  String get settingsHubServerOnPhone => 'En este teléfono';

  @override
  String settingsHubServerLine(String where, String state) {
    return '$where · $state';
  }

  @override
  String get settingsHubServerNone => 'Aún sin configurar';

  @override
  String gitSaveAndSendTo(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Guardar $count archivos y enviar a $place · $branch',
      one: 'Guardar 1 archivo y enviar a $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String gitSendSavesTo(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Enviar $count saves a $place · $branch',
      one: 'Enviar 1 save a $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String get gitSaveAndSendTitle => 'Guardar y enviar';

  @override
  String get gitSaveOnly => 'Guardar sin enviar';

  @override
  String gitMessageAddOne(String file) {
    return 'Agregar $file';
  }

  @override
  String gitMessageUpdateOne(String file) {
    return 'Actualizar $file';
  }

  @override
  String gitMessageUpdateTwo(String first, String second) {
    return 'Actualizar $first y $second';
  }

  @override
  String gitMessageUpdateMany(String first, int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Actualizar $first y $count archivos más',
      one: 'Actualizar $first y 1 archivo más',
    );
    return '$_temp0';
  }

  @override
  String gitDoneSavedAndSent(String place, String branch) {
    return 'Guardado y enviado a $place · $branch.';
  }

  @override
  String gitSavedNotSent(String reason) {
    return 'Guardado aquí, aún sin enviar. $reason';
  }

  @override
  String get gitMoreLabel => 'Remotos, clonar y detalles';

  @override
  String gitBusySendingTo(String place, String branch) {
    return 'Enviando a $place · $branch…';
  }

  @override
  String gitGetNewFrom(int count, String place, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Conseguir $count cambios nuevos de $place · $branch',
      one: 'Conseguir 1 cambio nuevo de $place · $branch',
    );
    return '$_temp0';
  }

  @override
  String gitSavedSignInFirst(String place) {
    return 'Guardado aquí, aún sin enviar. $place necesita que inicies sesión primero.';
  }

  @override
  String get thisPhoneAnswerRunning => 'Todo corre';

  @override
  String thisPhoneAnswerMemory(String size) {
    return 'Usa cerca de $size de memoria';
  }

  @override
  String thisPhoneAnswerStorage(String size) {
    return 'Toma $size de almacenamiento';
  }

  @override
  String get thisPhoneAnswerConnect => 'Usa el servidor de este teléfono';

  @override
  String get thisPhoneAnswerHeatPaused => 'Pausado, tu teléfono está tibio';

  @override
  String get thisPhoneAnswerHeatStopped =>
      'Detenido, tu teléfono está caliente';

  @override
  String thisPhoneAnswerHeatResumeIn(String time) {
    return 'El AI Team se reanuda solo en $time';
  }

  @override
  String get thisPhoneAnswerHeatResumeCool =>
      'El AI Team se reanuda solo cuando tu teléfono se enfríe';

  @override
  String get thisPhoneAnswerHeatStays =>
      'El AI Team queda pausado hasta que lo reanudes';

  @override
  String get thisPhoneAnswerOff => 'El servidor está apagado';

  @override
  String get thisPhoneAnswerOffLine =>
      'Se queda apagado hasta que lo enciendas.';

  @override
  String get thisPhoneAnswerAndroidClosed => 'Android cerró el servidor';

  @override
  String get thisPhoneAnswerStoppedItself => 'El servidor se detuvo solo';

  @override
  String thisPhoneAnswerRestartsIn(String time) {
    return 'Se reinicia solo en $time';
  }

  @override
  String get thisPhoneAnswerRestartsSoon => 'Se reinicia solo en un momento';

  @override
  String get thisPhoneAnswerRestarting => 'Reiniciándose solo';

  @override
  String get thisPhoneAnswerGaveUp =>
      'Siguió deteniéndose, así que ya no se reinicia solo.';

  @override
  String get thisPhoneAnswerRestartNow => 'Reiniciar ahora';

  @override
  String get thisPhoneAnswerStartFailed => 'El servidor no arrancó';

  @override
  String get thisPhoneAnswerInstallFailed => 'La configuración no terminó';

  @override
  String get thisPhoneAnswerCheckFailed => 'No se pudo revisar el servidor';

  @override
  String get thisPhoneAnswerStopFailed => 'El servidor no se detuvo';

  @override
  String get thisPhoneAnswerRecovery =>
      'El servidor no puede reiniciarse con seguridad';

  @override
  String get thisPhoneAnswerNotSetUp => 'OpenCode aún no está en este teléfono';

  @override
  String get thisPhoneAnswerSettingUp => 'Configurando el servidor';

  @override
  String get thisPhoneAnswerStarting => 'Arrancando el servidor';

  @override
  String get thisPhoneAnswerStopping => 'Deteniendo el servidor';

  @override
  String get thisPhoneAnswerChecking => 'Revisando el servidor';

  @override
  String get thisPhoneAnswerRemoving => 'Quitando el servidor';

  @override
  String get thisPhoneAnswerConnecting => 'Conectando al servidor';

  @override
  String get thisPhoneRestartRow => 'Reiniciar OpenCode en este teléfono';

  @override
  String get thisPhoneRestartTitle => '¿Reiniciar el servidor?';

  @override
  String get thisPhoneRestartBusy =>
      'Se está escribiendo una respuesta. Reiniciar la detiene; tus conversaciones se conservan.';

  @override
  String thisPhoneAboutSeconds(int seconds) {
    return 'cerca de $seconds s';
  }

  @override
  String thisPhoneAboutMinutes(int minutes) {
    return 'cerca de $minutes min';
  }

  @override
  String get thisPhoneAnswerStartFailedLine =>
      'Arráncalo otra vez; Detalles, abajo, dice qué salió mal.';

  @override
  String get serverSwitcherAddServer => 'Agregar un servidor';

  @override
  String get serverSwitcherNotAnswering => 'No responde';

  @override
  String serverSwitcherChecksAgain(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'revisa otra vez en $count min',
      one: 'revisa otra vez en 1 min',
    );
    return '$_temp0';
  }

  @override
  String serverSwitcherTryAgainLabel(String server) {
    return 'Reintentar $server';
  }

  @override
  String get serverSwitcherOpenRequest => 'Abrir';

  @override
  String serverSwitcherOpenRequestLabel(String target) {
    return 'Abrir $target';
  }

  @override
  String serverSwitcherAllowLabel(String command, String server) {
    return 'Permitir $command una vez en $server';
  }

  @override
  String serverSwitcherUndoLabel(String server) {
    return 'Deshacer la respuesta en $server';
  }

  @override
  String kitRequestMoreTargets(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '+$count más',
      one: '+1 más',
    );
    return '$_temp0';
  }

  @override
  String kitRequestChangeSize(int added, int removed) {
    return '+$added −$removed';
  }

  @override
  String chatRequestAskRunIn(String command, String project) {
    return '¿Correr $command en $project?';
  }

  @override
  String chatRequestAskRunNamed(String command) {
    return '¿Correr $command?';
  }

  @override
  String chatRequestAskRunAnyIn(String project) {
    return '¿Correr un comando en $project?';
  }

  @override
  String chatRequestAskEditNamed(String file) {
    return '¿Editar $file?';
  }

  @override
  String chatRequestAskEditFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '¿Editar $count archivos?',
      one: '¿Editar 1 archivo?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskCreateNamed(String file) {
    return '¿Crear $file?';
  }

  @override
  String chatRequestAskCreateFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '¿Crear $count archivos?',
      one: '¿Crear 1 archivo?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskDeleteNamed(String file) {
    return '¿Eliminar $file?';
  }

  @override
  String chatRequestAskDeleteFiles(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '¿Eliminar $count archivos?',
      one: '¿Eliminar 1 archivo?',
    );
    return '$_temp0';
  }

  @override
  String chatRequestAskReadNamed(String file) {
    return '¿Leer $file?';
  }

  @override
  String chatRequestAlwaysAllowNamed(String what) {
    return 'Permitir siempre $what';
  }

  @override
  String get chatRequestAlwaysAllowEdits => 'Permitir siempre las ediciones';

  @override
  String get chatRequestAlwaysAllowAnyCommand =>
      'Permitir siempre todos los comandos';

  @override
  String get chatRequestAlwaysEditsWhat => 'las ediciones';

  @override
  String get chatRequestAlwaysAnyCommandWhat => 'todos los comandos';

  @override
  String get filesAskAboutFile => 'Preguntar sobre este archivo';

  @override
  String filesMenuChanges(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Cambios · $count archivos',
      one: 'Cambios · 1 archivo',
      zero: 'Cambios',
    );
    return '$_temp0';
  }

  @override
  String filesProjectSwitchLabel(String name) {
    return 'Proyecto $name. Cambiar de proyecto';
  }

  @override
  String get scheduleNew => 'Mensaje programado nuevo';

  @override
  String get scheduleAsk => '¿Qué debería hacer?';

  @override
  String get scheduleAskHint => 'Correr las pruebas y resumir qué falló';

  @override
  String get scheduleEveryDay => 'Todos los días';

  @override
  String get scheduleWeekdaysShort => 'Días de semana';

  @override
  String get scheduleCustom => 'Personalizado';

  @override
  String scheduleRuleDaily(String time) {
    return 'Todos los días a las $time';
  }

  @override
  String scheduleRuleWeekdays(String time) {
    return 'Todos los días de semana a las $time';
  }

  @override
  String scheduleRuleHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Cada $hoursString horas',
      one: 'Cada hora',
    );
    return '$_temp0';
  }

  @override
  String scheduleForTime(String time) {
    return 'Programar para las $time';
  }

  @override
  String scheduleForWhen(String when) {
    return 'Programar para $when';
  }

  @override
  String scheduleForHours(int hours) {
    final intl.NumberFormat hoursNumberFormat =
        intl.NumberFormat.decimalPattern(localeName);
    final String hoursString = hoursNumberFormat.format(hours);

    String _temp0 = intl.Intl.pluralLogic(
      hours,
      locale: localeName,
      other: 'Programar cada $hoursString horas',
      one: 'Programar cada hora',
    );
    return '$_temp0';
  }

  @override
  String scheduleForToday(String time) {
    return 'Programar para hoy, $time';
  }

  @override
  String scheduleForTomorrow(String time) {
    return 'Programar para mañana, $time';
  }

  @override
  String scheduleDayAt(String day, String time) {
    return '$day, $time';
  }

  @override
  String scheduleSent(String when) {
    return 'Se envió $when';
  }

  @override
  String scheduleNotSent(String when) {
    return 'No salió $when';
  }

  @override
  String scheduleSkippedWhen(String when) {
    return 'Se omitió $when';
  }

  @override
  String get scheduleSendingNow => 'Enviando ahora';

  @override
  String get scheduleDayToday => 'hoy';

  @override
  String get scheduleDayYesterday => 'ayer';

  @override
  String get scheduleWheelToday => 'Hoy';

  @override
  String get scheduleWheelTomorrow => 'Mañana';

  @override
  String get scheduleWheelDay => 'Día';

  @override
  String get scheduleWheelHowOften => 'Con qué frecuencia';

  @override
  String get scheduleClosedNote =>
      'Corre aun con la app cerrada, a veces con un poco de retraso.';

  @override
  String get scheduleNeedPrompt => 'Escribe primero qué debería hacer.';

  @override
  String get scheduleNeedFuture => 'Elige una hora más tarde que ahora.';

  @override
  String get scheduleInThisConversation => 'En esta conversación';

  @override
  String get scheduleOpenRun => 'Abrir la conversación';

  @override
  String get scheduleDigestShort => 'Una nota cada mañana sobre lo que corrió';

  @override
  String get scheduleExactNote => 'Los inicios pueden retrasarse un poco.';

  @override
  String get scheduleExactAllow => 'Permitir la exactitud de la hora';

  @override
  String get scheduleNotifyOff =>
      'Las notificaciones están apagadas, así que un inicio no te avisará de que salió.';

  @override
  String get inboxTitle => 'Te espera';

  @override
  String get inboxEmptyTitle => 'Nada te espera';

  @override
  String get inboxFinishedLabel => 'Terminó, aún sin abrir';

  @override
  String inboxOpenLabel(String conversation) {
    return 'Abrir $conversation';
  }

  @override
  String inboxOpenOnServer(String server) {
    return 'Abrir en $server';
  }

  @override
  String get inboxWaitingForAnswer => 'Esperando tu respuesta';

  @override
  String get inboxAsksPermission => 'Pide tu permiso';

  @override
  String get inboxAsksQuestion => 'Te hace una pregunta';

  @override
  String inboxUnreachable(String servers) {
    return '$servers no responde. Lo que espera allí no se muestra.';
  }

  @override
  String get kitRequestOpen => 'Abrir';

  @override
  String goToNeedsYouHint(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count esperando',
      one: '1 esperando',
      zero: 'Nada espera',
    );
    return '$_temp0';
  }

  @override
  String inboxEmptyPartialLine(String servers) {
    return '$servers no responde, así que puede que tenga más';
  }

  @override
  String get kitFieldOptional => 'Opcional';

  @override
  String get reportProblemAttachedLead =>
      'Los detalles del error y la información de la app van adjuntos.';

  @override
  String get reportProblemJobLead =>
      'El log del trabajo, los detalles del error y la información de la app van adjuntos.';

  @override
  String get reportProblemJobNoLogLead =>
      'No se conservó ningún log para este trabajo. Los detalles del error y la información de la app van adjuntos.';

  @override
  String reportProblemKeptLead(String when) {
    return 'Notado $when. Sus detalles y la información de la app van adjuntos.';
  }

  @override
  String get reportProblemNothingTitle => 'No se notó ningún error';

  @override
  String get reportProblemNothingLead =>
      'Di qué salió mal. Tus palabras y la información de la app van con el reporte.';

  @override
  String get reportProblemPrivacy =>
      'Las contraseñas, claves y direcciones de servidores se quitan.';

  @override
  String get reportProblemAddLabel => '¿Algo por agregar?';

  @override
  String get reportProblemAddHint => '¿Qué estabas haciendo cuando ocurrió?';

  @override
  String get reportProblemSendGitHub => 'Enviar como un issue de GitHub';

  @override
  String get reportProblemSendHint =>
      'Revísalo en GitHub antes de publicarlo. Será público.';

  @override
  String get reportProblemCopyShort => 'Copiar';

  @override
  String get reportProblemKeptLink => 'Todo lo que este teléfono conservó';

  @override
  String welcomePhoneCost(String time, String size) {
    return '$time · $size de descarga';
  }

  @override
  String get welcomePhoneSetUp => 'Configurar en este teléfono';

  @override
  String welcomePhoneSettingUp(int percent) {
    return 'Configurando · $percent %';
  }

  @override
  String welcomePhoneStopped(int percent) {
    return 'Configuración al $percent %';
  }

  @override
  String get welcomePhoneContinue => 'Seguir configurando';

  @override
  String get welcomePhoneReady => 'Instalado y listo';

  @override
  String get welcomePhoneOpen => 'Empezar en este teléfono';

  @override
  String get welcomeComputerLine =>
      'Vincula con un código o escribe su dirección';

  @override
  String get welcomeComputerConnect => 'Conectar con mi computadora';

  @override
  String get welcomeMoreWays => 'Otras formas de configurar';

  @override
  String kitProgressRingPercent(String percent) {
    return '$percent%';
  }

  @override
  String get kitProgressRingPercentSign => '%';

  @override
  String kitProgressRingSteps(String done, String total) {
    return '$done de $total';
  }

  @override
  String get kitProgressRingFinished => 'Terminado';

  @override
  String phoneSetupRingDownloading(String name) {
    return 'Descargando $name';
  }

  @override
  String phoneSetupRingUnpacking(String name) {
    return 'Desempaquetando $name';
  }

  @override
  String phoneSetupRingInstalling(String name) {
    return 'Instalando $name';
  }

  @override
  String get phoneSetupRingChecking => 'Revisando este teléfono';

  @override
  String get phoneSetupRingPausedTitle => 'La configuración está pausada';

  @override
  String get phoneSetupRingInterruptedTitle =>
      'La configuración se interrumpió';

  @override
  String get phoneSetupRingStoppedNote =>
      'Los pasos terminados se quedan, y las descargas retoman donde se quedaron.';

  @override
  String phoneSetupRingMinutesLeft(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: 'Cerca de $minutes min restantes',
      one: 'Cerca de 1 min restante',
    );
    return '$_temp0';
  }

  @override
  String get phoneSetupRingUnderMinute => 'Menos de un minuto restante';

  @override
  String get phoneSetupRingWifi => 'Wi-Fi';

  @override
  String get phoneSetupRingMobileData => 'Datos móviles';

  @override
  String get phoneSetupRingSafeToLeave =>
      'Puedes salir con seguridad. Te avisamos cuando esté listo.';

  @override
  String get phoneSetupRingSafeToLeaveQuiet =>
      'Puedes salir con seguridad. La configuración sigue en segundo plano.';

  @override
  String get phoneSetupRingPause => 'Pausar la configuración';

  @override
  String get phoneSetupRingDetailsTitle => 'Detalles de la configuración';

  @override
  String kitProgressRingOfTotal(String total) {
    return 'de $total';
  }

  @override
  String get kitProgressRingStepsDone => 'pasos hechos';

  @override
  String get devPreviewDetails => 'Detalles de la vista previa';

  @override
  String get devPreviewShowDetails => 'Mostrar los detalles';

  @override
  String get devPreviewAddress => 'Dirección';

  @override
  String get devPreviewAskHint => 'Pedir un cambio…';

  @override
  String get devPreviewAskSend => 'Enviar a la conversación';

  @override
  String get devPreviewAskStopAndSend => 'Detener al agente y enviar';

  @override
  String get devPreviewAskVoice => 'Habla tu cambio';

  @override
  String get devPreviewAskListening => 'Escuchando…';

  @override
  String get devPreviewAskDoneSpeaking => 'Terminaste de hablar';

  @override
  String get devPreviewAskSent => 'Enviado a la conversación';

  @override
  String get devPreviewAskSentJoins => 'Agregado a lo que el agente hace';

  @override
  String get devPreviewAskSentAfter =>
      'Enviado. Corre después de la respuesta actual';

  @override
  String get devPreviewAskSentRestart =>
      'Enviado. El agente empezó otra vez con ello';

  @override
  String get devPreviewAskNotSent => 'No se envió. Tus palabras siguen aquí';

  @override
  String get devPreviewAskNotHeard =>
      'No se entendió eso. Reintenta, o escríbelo';

  @override
  String teamMergeGateQuestion(int count, String branch) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '¿Fusionar $count cambios a $branch?',
      one: '¿Fusionar 1 cambio a $branch?',
      zero: '¿Fusionar los cambios a $branch?',
    );
    return '$_temp0';
  }

  @override
  String teamMergeGateChecksPassed(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'todas las $count revisiones pasaron',
      one: 'la revisión pasó',
    );
    return '$_temp0';
  }

  @override
  String get teamMergeGateApprove => 'Aprobar la fusión';

  @override
  String get teamMergeGateIfIgnored =>
      'Los cambios esperan en su rama. Nada se fusiona hasta que tú lo digas.';

  @override
  String teamMergeGateAnnouncement(String question) {
    return 'Listo para fusionar. $question';
  }

  @override
  String get teamMergeGateStale =>
      'La fusión queda apagada hasta que el equipo responda otra vez.';

  @override
  String teamTaskNowOn(String who, String step) {
    return 'Ahora: $who en $step';
  }

  @override
  String teamTaskNowWorker(String who) {
    return 'Ahora: $who está trabajando';
  }

  @override
  String get teamTaskNowWorking => 'Ahora: trabajando en tu tarea';

  @override
  String teamTaskNowStarting(String who) {
    return 'Ahora: $who está arrancando';
  }

  @override
  String get teamTaskNowStartingWorker => 'Ahora: arrancando un ejecutor';

  @override
  String get teamTaskNowWaitingWorker => 'Ahora: esperando a un ejecutor';

  @override
  String get teamTaskNowPlanning => 'Ahora: planificando los pasos';

  @override
  String get teamTaskNowReviewing => 'Ahora: revisando los cambios';

  @override
  String get teamTaskNextReview => 'Después: revisión';

  @override
  String get teamTaskNextMerge => 'Después: fusión';

  @override
  String get teamTaskNextWork => 'Después: el ejecutor comienza';

  @override
  String get teamTaskStepsDone => 'Todos los pasos hechos';

  @override
  String teamTaskFinishedAgo(String age) {
    return 'Terminó hace $age';
  }

  @override
  String get kitComposerTrayClose => 'Cerrar';

  @override
  String get composerTrayPhotos => 'Fotos';

  @override
  String get composerTrayCamera => 'Cámara';

  @override
  String get composerTrayFile => 'Archivo';

  @override
  String get composerTrayMore => 'Más';

  @override
  String chatLiveReading(String name) {
    return 'Leyendo $name';
  }

  @override
  String get chatLiveReadingAny => 'Leyendo un archivo';

  @override
  String chatLiveEditing(String name) {
    return 'Editando $name';
  }

  @override
  String get chatLiveEditingAny => 'Editando archivos';

  @override
  String chatLiveWriting(String name) {
    return 'Escribiendo $name';
  }

  @override
  String get chatLiveWritingAny => 'Escribiendo un archivo';

  @override
  String chatLiveRunning(String name) {
    return 'Corriendo $name';
  }

  @override
  String get chatLiveRunningAny => 'Corriendo un comando';

  @override
  String chatLiveSearching(String name) {
    return 'Buscando $name';
  }

  @override
  String get chatLiveSearchingAny => 'Buscando en el proyecto';

  @override
  String get chatLiveSearchingWeb => 'Buscando en la web';

  @override
  String get chatLivePlanning => 'Planificando los pasos';

  @override
  String get terminalSessionsLabel => 'Terminales abiertas';

  @override
  String get terminalSessionEnded => 'terminada';

  @override
  String localTerminalStopNamed(String name) {
    return 'Detener $name';
  }

  @override
  String localTerminalCloseNamed(String name) {
    return 'Cerrar $name';
  }

  @override
  String get terminalDevServerRunning => 'Servidor dev corriendo';

  @override
  String get terminalOpenPreview => 'Abrir la vista previa';

  @override
  String terminalPlaceLabel(String place) {
    return 'Corre en $place';
  }

  @override
  String terminalPlaceSwitchLabel(String place) {
    return 'Corre en $place, cambiar dónde corre';
  }

  @override
  String get terminalScreenDetailState => 'Estado';

  @override
  String get terminalStateRunning => 'Corriendo';

  @override
  String get terminalStateEnded => 'Terminó';

  @override
  String connectionTryingAgainIn(int seconds) {
    return 'reintentando en $seconds s';
  }

  @override
  String get connectionTryingAgainNow => 'reintentando ahora…';

  @override
  String get chatsHomeNothingRunning => 'Nada corriendo';

  @override
  String get chatsHomeNothingRunningBody =>
      'Empieza una conversación, o abre una para retomar donde te quedaste.';

  @override
  String get chatsHomeShowAll => 'Mostrar todas las conversaciones';

  @override
  String get chatsHomeEmptyCalmBody =>
      'Di qué quieres construir, y un agente se pone a trabajar.';

  @override
  String get chatsHomeConnectComputer => 'Conectar una computadora';

  @override
  String get chatsHomeProjectEmptyBody =>
      'Pide algo en este proyecto, y aparecerá aquí.';

  @override
  String get scheduleEmptyBody =>
      'Elige una hora, y este teléfono envía el mensaje por ti.';

  @override
  String get projectsLoadFailedTitle => 'Los proyectos no cargaron';

  @override
  String get chatStopAfterStep => 'Detener después de este paso';

  @override
  String get chatStopAfterStepPrompt =>
      'Por favor detente después del paso en el que estás ahora y espera a mí antes de hacer nada más.';

  @override
  String get appearanceModeAuto => 'Auto';

  @override
  String get appearanceModeSaver => 'Ahorro';

  @override
  String get appearanceSaverNote =>
      'Negro puro, sin movimiento. Ahorra batería en pantallas OLED.';

  @override
  String get appearanceAccentTitle => 'Acento';

  @override
  String get appearanceAccentMint => 'Menta';

  @override
  String get appearanceAccentTeal => 'Verde azulado';

  @override
  String get appearanceAccentBlue => 'Azul';

  @override
  String get appearanceAccentViolet => 'Violeta';

  @override
  String get appearanceAccentPink => 'Rosa';

  @override
  String get appearanceAccentMono => 'Mono';

  @override
  String get appearanceAccentSilver => 'Plata';

  @override
  String get appearanceSurfaceSolid => 'Sólido';

  @override
  String get appearanceSurfaceSoft => 'Suave';

  @override
  String get appearanceSurfaceBrutal => 'Brutal';

  @override
  String get appearanceSurfacePaper => 'Papel';

  @override
  String get signalStyleName => 'Señal';

  @override
  String get signalShow => 'Mostrar';

  @override
  String get signalHide => 'Ocultar';

  @override
  String signalRowRunningCommand(String command) {
    return 'Corriendo $command';
  }

  @override
  String get appearanceFineTuneCorners => 'Esquinas';

  @override
  String get appearanceFineTuneCornersRound => 'Redondas';

  @override
  String get appearanceFineTuneCornersSoft => 'Suaves';

  @override
  String get appearanceFineTuneCornersSharp => 'Nítidas';

  @override
  String get appearanceFineTuneDepth => 'Profundidad';

  @override
  String get appearanceFineTuneEdges => 'Bordes';

  @override
  String appearanceFineTuneReset(String style) {
    return 'Reajustar a los valores por defecto de $style';
  }

  @override
  String get kitTourNext => 'Siguiente';

  @override
  String get kitTourSkip => 'Saltar';

  @override
  String get kitTourDone => 'Listo';

  @override
  String kitTourStep(int current, int total) {
    return 'Paso $current de $total';
  }

  @override
  String get tipStopAfterStep =>
      'Mantén presionado para detener después de este paso';

  @override
  String get tipStopAfterStepTitle => 'Detener después de este paso';

  @override
  String get tipSendLater => 'Mantén Enviar para enviar después o repetir';

  @override
  String get tipSendLaterTitle => 'Enviar después o repetir';

  @override
  String get tourAppearanceStart => 'Muéstrame cómo · 30 s';

  @override
  String get tourNotNow => 'Ahora no';

  @override
  String get tipsTitle => 'Consejos';

  @override
  String get tipsHubSubtitle => 'Gestos ocultos y recorridos cortos';

  @override
  String get tipsSearchAliases =>
      'consejos tutorial recorrido pistas gestos mantener pulsado pulsación larga cómo aprender';

  @override
  String get tipsIntro =>
      'Cada consejo aparece en su momento, como mucho dos veces. Reproduce cualquiera de ellos aquí.';

  @override
  String get tipsShowMe => 'Muéstrame';

  @override
  String get tipsTipsLabel => 'Consejos';

  @override
  String get tipsToursLabel => 'Mini tutoriales';

  @override
  String get chatStopAfterStepPending => 'Deteniéndose después de este paso';

  @override
  String get chatStopAfterStepUndo => 'Deshacer';

  @override
  String get chatStopAfterStepUndone => 'Seguirá su marcha.';

  @override
  String get chatStopAfterStepTooLate =>
      'Demasiado tarde: el agente ya tiene la nota.';

  @override
  String get chatLiveShowSteps => 'Mostrar los pasos';

  @override
  String get chatLiveHideSteps => 'Ocultar los pasos';

  @override
  String get kitComposerStopPending => 'deteniéndose después de este paso';

  @override
  String get phraseChipsRetryStarter => 'Reintentar';

  @override
  String get tourMakeItYoursTitle => 'Hazlo tuyo';

  @override
  String get tourMakeItYoursOffer =>
      'Hazlo tuyo: claro u oscuro, colores y estilo, en cuatro pasos';

  @override
  String get tourMakeItYoursColours =>
      'Empieza aquí: claro u oscuro, un tema y un acento. La vista previa de arriba muestra cada cambio a la vez.';

  @override
  String get tourMakeItYoursStyle =>
      'Elige cómo se sienten las tarjetas y las barras: Sólido, Suave, Brutal o Papel.';

  @override
  String get kitGuideChanges => 'Cambios';

  @override
  String get kitGuideAll => 'Todo';

  @override
  String get kitGuideBack => 'Volver a todos los chips';

  @override
  String get appearanceGroupColours => 'Colores';

  @override
  String get appearanceGroupStyle => 'Estilo';

  @override
  String get appearanceGroupBackground => 'Fondo';

  @override
  String get appearanceGroupComfort => 'Texto y movimiento';

  @override
  String appearanceGroupPair(String first, String second) {
    return '$first, $second';
  }

  @override
  String get appearanceChipMode => 'Modo';

  @override
  String get appearanceChipTheme => 'Tema';

  @override
  String get appearanceChipAccent => 'Acento';

  @override
  String get appearanceChipStyle => 'Estilo';

  @override
  String get appearanceChipShape => 'Forma';

  @override
  String get appearanceChipLook => 'Aspecto';

  @override
  String get appearanceChipStrength => 'Intensidad';

  @override
  String get appearanceChipText => 'Tamaño del texto';

  @override
  String get appearanceChipMotion => 'Movimiento';

  @override
  String get appearanceChipGlow => 'Brillo de la respuesta';

  @override
  String get appearanceAskMode => '¿Claro u oscuro?';

  @override
  String get appearanceAskTheme => '¿Qué colores?';

  @override
  String get appearanceAskAccent => '¿Qué color de acento?';

  @override
  String get appearanceAskStyle => '¿Cómo se deberían sentir las superficies?';

  @override
  String get appearanceAskShape => 'Esquinas y espaciado';

  @override
  String get appearanceAskLook => '¿Qué se mueve detrás de tus páginas?';

  @override
  String get appearanceAskStrength => '¿Con cuánta intensidad?';

  @override
  String get appearanceAskText => '¿Qué tan grande debería ser el texto?';

  @override
  String get appearanceAskMotion => '¿Cuánto se debería mover?';

  @override
  String get appearanceAskGlow => '¿Brillo mientras se escribe una respuesta?';

  @override
  String get appearanceChangesMode => 'Cada página, claro u oscuro';

  @override
  String get appearanceChangesTheme => 'Los colores de cada página';

  @override
  String get appearanceChangesAccent =>
      'Botones, enlaces, la tecla de enviar y el brillo';

  @override
  String get appearanceChangesStyle =>
      'Cada tarjeta, barra y burbuja de mensaje';

  @override
  String get appearanceChangesShape =>
      'Las esquinas y el espaciado de este estilo';

  @override
  String get appearanceChangesLook => 'El fondo de cada página';

  @override
  String get appearanceChangesStrength =>
      'Cuánto se ve del fondo a través de él';

  @override
  String get appearanceChangesText =>
      'El texto en todas partes, sobre el tamaño de tu teléfono';

  @override
  String get appearanceChangesMotion => 'Las animaciones en toda la app';

  @override
  String get appearanceChangesGlow => 'El borde de la caja de mensajes';

  @override
  String get appearanceOffInSaver => 'Apagado en Ahorro';

  @override
  String get appearanceLookOff => 'Apagado';

  @override
  String get appearanceStrengthNoLook => 'Sin aspecto';

  @override
  String appearanceShapeValue(String corners, String spacing) {
    return '$corners, $spacing';
  }

  @override
  String get appearanceShapeSpacing => 'Espaciado';

  @override
  String get appearanceSpacingRoomy => 'Amplio';

  @override
  String get appearanceSpacingCompact => 'Compacto';

  @override
  String appearanceShapeNote(String style, String corners, String spacing) {
    return '$style empieza en $corners y $spacing. Elegir otro estilo los empieza de nuevo.';
  }

  @override
  String get appearanceMotionPhraseFull => 'movimiento completo';

  @override
  String get appearanceMotionPhraseCalm => 'movimiento tranquilo';

  @override
  String get appearanceMotionPhraseOff => 'sin movimiento';

  @override
  String get appearanceGlowOff => 'Apagado';

  @override
  String get appearanceGlowOffHint => 'Un borde sencillo';

  @override
  String get appearanceGlowClassicHint => 'Una luz que rodea la caja';

  @override
  String get appearanceGlowSoftHint => 'Un anillo que respira despacio';

  @override
  String get appearanceModeAutoNote =>
      'Sigue a tu teléfono: claro de día, oscuro de noche.';

  @override
  String appearanceSeeAllThemes(int count) {
    return 'Ver los $count temas';
  }

  @override
  String appearanceSeeAllLooks(int count) {
    return 'Ver los $count aspectos';
  }

  @override
  String appearanceAccentMatchNote(String theme) {
    return '“Igualar” usa el acento propio de $theme.';
  }

  @override
  String get appearanceStrengthNeedsLook =>
      'El fondo está apagado, así que no hay nada que reforzar.';

  @override
  String get appearanceChooseLookFirst => 'Elige primero un aspecto';

  @override
  String get appearanceSaverHoldsStyle =>
      'Ahorro mantiene las tarjetas sólidas y el fondo quieto, para ahorrar batería en esta pantalla. Tu estilo vuelve cuando Ahorro se apaga.';

  @override
  String get appearanceSaverHoldsBackground =>
      'Ahorro mantiene las tarjetas sólidas y el fondo quieto, para ahorrar batería en esta pantalla. Tu fondo vuelve cuando Ahorro se apaga.';

  @override
  String get appearanceTurnOffSaver => 'Apagar Ahorro';

  @override
  String get appearanceSaverMotion => 'Ahorro mantiene el movimiento apagado.';

  @override
  String get appearanceGuideStart =>
      'Empieza con Colores, el cambio más grande. Cada elección se guarda sobre la marcha, y puedes tocar cualquier fila en cualquier momento.';

  @override
  String get appearanceGuideSkip => 'Saltar la guía';

  @override
  String get appearanceGuideResume =>
      'Retoma donde te quedaste, o toca cualquier chip.';

  @override
  String get appearanceGuideAllSetTitle => 'Todo listo.';

  @override
  String get appearanceGuideAllSet =>
      'Tus elecciones están guardadas. Toca cualquier chip para cambiar una.';

  @override
  String get appearanceGuideAgain => 'Guíame otra vez';

  @override
  String get appearanceGuideReset => 'Reajustar a los valores por defecto';

  @override
  String get appearanceGuideResetConfirm =>
      '¿Devolver cada elección de Apariencia a los valores por defecto? Claro u oscuro, tema, acento, estilo, fondo, tamaño del texto, movimiento y el brillo de la respuesta se reajustan.';

  @override
  String get appearanceGuideResetDo => 'Reajustar';

  @override
  String get appearanceGuideResetKeep => 'Conservar mis elecciones';

  @override
  String appearanceGuideCountIntro(int count) {
    return '$count preguntas rápidas, todas opcionales';
  }

  @override
  String appearanceGuideCountSeen(int seen, int total) {
    return '$seen de $total vistas';
  }

  @override
  String appearanceGuideCountAt(int at, int total) {
    return '$at de $total';
  }

  @override
  String get appearanceGuideSavesAsYouGo => 'Todo se guarda sobre la marcha';

  @override
  String get appearanceGuideStartButton => 'Empezar con Colores';

  @override
  String appearanceGuideContinue(String group) {
    return 'Continuar: $group';
  }

  @override
  String appearanceGuideNext(String name) {
    return 'Después: $name';
  }

  @override
  String get appearanceGuideFinish => 'Terminar';

  @override
  String appearanceGuidePlace(
    String label,
    String value,
    int at,
    int count,
    String group,
  ) {
    return '$label, $value, pregunta $at de $count en $group';
  }

  @override
  String get tourMakeItYoursBackground =>
      'Elige qué se mueve detrás de tus páginas, o apágalo.';

  @override
  String get tourMakeItYoursComfort =>
      'Haz el texto más grande, calma el movimiento y elige el brillo mientras se escribe una respuesta.';

  @override
  String get appearanceAccentMatchShort => 'Igualar';

  @override
  String get chatTurnStoppedElsewhere =>
      'Esta respuesta se detuvo desde otra app o dispositivo.';

  @override
  String get chatTurnPausedInactive =>
      'El servidor pausó esta respuesta tras una hora sin trabajo nuevo.';

  @override
  String get chatTurnStoppedServerRestart =>
      'El servidor se reinició, así que esta respuesta se detuvo.';

  @override
  String get chatTurnSuperseded =>
      'Una corrida más nueva tomó el lugar de esta respuesta.';

  @override
  String get chatTurnStoppedEarly =>
      'Esta respuesta se detuvo antes de terminar.';

  @override
  String get settingsV3ModelNewOnPhone =>
      'Conversaciones nuevas en este teléfono';

  @override
  String chatLoadAgentMissing(String agent) {
    return '$agent en este teléfono no tiene esta conversación. Vuelve y ábrela desde tus conversaciones.';
  }

  @override
  String chatLoadAgentUnreadable(String agent) {
    return '$agent en este teléfono respondió, pero esta app no pudo leer la respuesta. Reintenta, o reporta un problema.';
  }

  @override
  String get productErrorUnreadableReply =>
      'Respondió, pero esta app no pudo leer la respuesta. Reintenta, o reporta un problema.';

  @override
  String get settingsV3SavedOnPhone =>
      'Guardado en este teléfono. Otras apps que usen el servidor de este teléfono reciben esto también.';

  @override
  String settingsV3UseMyDefaultDonePhone(String what) {
    return 'Reajustado en este teléfono a tus propias elecciones: $what.';
  }

  @override
  String get chatStallSubagent =>
      'Este sub-agente lleva un rato callado. Se detiene con su conversación principal: detén la respuesta allí.';

  @override
  String get serverPageThisPhonesServer => 'el servidor de este teléfono';

  @override
  String kitBackgroundWorkOne(String task, String time) {
    return 'Sigue corriendo en segundo plano · $task · $time';
  }

  @override
  String kitBackgroundWorkMany(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tareas siguen corriendo en segundo plano',
      one: '1 tarea sigue corriendo en segundo plano',
    );
    return '$_temp0';
  }

  @override
  String kitBackgroundWorkTask(String task, String time) {
    return '$task · $time';
  }

  @override
  String kitBackgroundWorkMinutes(int minutes) {
    String _temp0 = intl.Intl.pluralLogic(
      minutes,
      locale: localeName,
      other: '$minutes min',
      zero: 'menos de un minuto',
    );
    return '$_temp0';
  }

  @override
  String kitBackgroundWorkHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String get kitBackgroundWorkGoToStep => 'Muestra el paso que lo arrancó';

  @override
  String kitBackgroundWorkOneAfterStop(String task, String time) {
    return 'Sigue corriendo después de que el turno se detuvo · $task · $time';
  }

  @override
  String kitBackgroundWorkManyAfterStop(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count tareas siguen corriendo después de que el turno se detuvo',
      one: '1 tarea sigue corriendo después de que el turno se detuvo',
    );
    return '$_temp0';
  }

  @override
  String chatAgentTurnLostConnectionTo(String agent, String company) {
    return '$agent perdió su conexión con $company, así que el turno se detuvo.';
  }

  @override
  String chatAgentTurnLostConnection(String agent) {
    return '$agent perdió su conexión con su proveedor de modelo, así que el turno se detuvo.';
  }

  @override
  String chatSendFailureLostConnectionTo(String agent, String company) {
    return '$agent perdió su conexión con $company';
  }

  @override
  String chatSendFailureLostConnection(String agent) {
    return '$agent perdió su conexión con su proveedor de modelo';
  }

  @override
  String chatBackgroundWorkWaitingFor(String program) {
    return 'Esperando a $program';
  }

  @override
  String chatSendFailureSignIn(String agent) {
    return '$agent necesita que inicies sesión';
  }

  @override
  String chatSendFailureNoCredit(String agent) {
    return '$agent no tiene créditos para este modelo';
  }

  @override
  String get chatSendFailureModelUnavailable =>
      'Este modelo no está disponible';

  @override
  String chatSendFailureBusy(String agent) {
    return '$agent está ocupado';
  }

  @override
  String get chatSendFailureAgentFallback => 'El agente';

  @override
  String get chatSendFailureSignInAction => 'Iniciar sesión';

  @override
  String get chatSendFailureAccountAction => 'Abrir la cuenta';

  @override
  String get chatSendFailureDetailsAgent => 'Agente';

  @override
  String get chatSendFailureDetailsModel => 'Modelo';

  @override
  String get chatSendFailureDetailsCode => 'Código del error';

  @override
  String get chatSendFailureNewConversation =>
      'Este agente no puede continuar esta conversación';

  @override
  String chatSendFailureNeedsAccount(String agent) {
    return 'Este modelo necesita una cuenta de $agent';
  }

  @override
  String chatSendFailureSignInToAgent(String agent) {
    return 'Iniciar sesión en $agent';
  }

  @override
  String get chatComposerNoteDismiss => 'Quitar la nota';

  @override
  String get chatAnnounceReplyFinished => 'Respuesta terminada';

  @override
  String get chatAnnounceNeedsYou => 'El agente te necesita';

  @override
  String get runningPhoneTitle => 'Corriendo en este teléfono';

  @override
  String runningPhoneSummary(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count corriendo · $memory',
      one: '1 corriendo · $memory',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneAutoRefresh =>
      'Revisa otra vez cada 5 segundos mientras está abierta';

  @override
  String get runningPhoneLimitTitle => 'Los servidores pueden detenerse';

  @override
  String runningPhoneLimitBody(int count, int limit) {
    return '$count de $limit procesos en segundo plano: por encima de $limit, Android detiene primero los procesos más viejos de esta app, y esos son los servidores. Detén lo que quedó atrás o no hace falta, o sube el límite con Opciones de desarrollador › Desactivar las restricciones de procesos secundarios.';
  }

  @override
  String runningPhoneGroup(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'leftBehind': 'Abandonado',
      'openCode': 'OpenCode',
      'claudeCode': 'Claude Code',
      'agentHelper': 'Ayudante de agente',
      'aiTeam': 'AI Team',
      'terminal': 'Terminal',
      'setup': 'Configuración y revisiones',
      'helpers': 'Ayudantes',
      'app': 'Esta app',
      'other': 'Otro',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneGroupChat(String title) {
    return 'Claude Code · $title';
  }

  @override
  String runningPhoneName(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'flutterTest': 'Prueba de Flutter',
      'command': 'Comando',
      'linux': 'Linux',
      'openCode': 'OpenCode',
      'claudeCode': 'Claude Code',
      'agentHelper': 'Ayudante de agente',
      'signIn': 'Inicio de sesión de agente',
      'team': 'Motor de AI Team',
      'node': 'Node.js',
      'python': 'Python',
      'git': 'Git',
      'npm': 'npm',
      'dart': 'Dart',
      'flutter': 'Flutter',
      'gradle': 'Gradle',
      'java': 'Java',
      'search': 'Búsqueda',
      'app': 'Esta app',
      'other': 'Programa',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneCpu(String level, String percent) {
    String _temp0 = intl.Intl.selectLogic(level, {
      'idle': 'Inactivo',
      'light': 'Ligero',
      'busy': 'Ocupado',
      'other': 'Muy ocupado',
    });
    return '$_temp0 · $percent%';
  }

  @override
  String get runningPhoneMarkHeavy => 'Pesado';

  @override
  String get runningPhoneMarkStuck => 'Puede estar estancado';

  @override
  String get runningPhoneMarkLeftBehind => 'Abandonado';

  @override
  String runningPhoneFromChat(String title) {
    return 'Lo arrancó Claude Code · $title';
  }

  @override
  String get runningPhoneFromClaude => 'Lo arrancó Claude Code';

  @override
  String get runningPhoneStop => 'Detener';

  @override
  String runningPhoneStopNamed(String name) {
    return 'Detener $name';
  }

  @override
  String runningPhoneStopTitle(String name) {
    return '¿Detener $name?';
  }

  @override
  String get runningPhoneStopBody =>
      'Primero recibe una detención amable y, después, una forzada si sigue corriendo.';

  @override
  String runningPhoneStopAllLeftBehind(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Detener a los $count abandonados',
      one: 'Detener al único abandonado',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneStopAllDetail =>
      'Nada más los detiene, y detenerlos es seguro.';

  @override
  String get runningPhoneStopAllTitle => '¿Detener todo lo abandonado?';

  @override
  String runningPhoneStopAllBody(String names) {
    return '$names. Cada uno recibe primero una detención amable y, después, una forzada si sigue corriendo.';
  }

  @override
  String runningPhoneStopped(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: 'Se detuvieron $count',
      one: 'Se detuvo 1',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneAlreadyEnded(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count ya habían terminado',
      one: '1 ya había terminado',
    );
    return '$_temp0';
  }

  @override
  String get runningPhoneNotStoppedTitle => 'No todo se detuvo';

  @override
  String runningPhoneRefusedApp(String name) {
    return '$name: esta app misma no se puede detener desde aquí.';
  }

  @override
  String runningPhoneRefusedServer(String name) {
    return '$name: corre un servidor. Detén el servidor desde En este teléfono.';
  }

  @override
  String runningPhoneRefusedStillRunning(String name) {
    return '$name: siguió corriendo tras una detención forzada. Reintenta, o reinicia el teléfono.';
  }

  @override
  String get runningPhoneStopFailed => 'No se pudo detener. Reintenta.';

  @override
  String get runningPhoneLoadFailedTitle => 'No se pudo leer lo que corre';

  @override
  String get runningPhoneLoadFailedBody =>
      'El teléfono no respondió. Reintenta.';

  @override
  String get runningPhoneRefreshFailed =>
      'No se pudo revisar otra vez, así que esto muestra la última lectura.';

  @override
  String get runningPhoneEmpty => 'Nada más corre';

  @override
  String get runningPhoneEmptyBody =>
      'Cuando OpenCode, Claude Code o una terminal corran aquí, aparecerán en esta lista.';

  @override
  String runningPhoneAbout(String kind) {
    String _temp0 = intl.Intl.selectLogic(kind, {
      'leftBehind':
          'Quedó corriendo después de que su Linux terminó. Nada lo espera, así que detenerlo es seguro.',
      'openCode': 'Parte del servidor de OpenCode en este teléfono.',
      'claudeCode': 'Parte de una conversación de Claude Code.',
      'agentHelper': 'Corre Claude Code y otros agentes en este teléfono.',
      'aiTeam': 'Parte del AI Team.',
      'terminal': 'Corre en una terminal en este teléfono.',
      'setup': 'Un paso de configuración o una revisión.',
      'helpers': 'Un servicio de ayuda de esta app.',
      'app': 'Esta app misma.',
      'other': 'Lo arrancó esta app.',
    });
    return '$_temp0';
  }

  @override
  String runningPhoneHeavyFor(String elapsed) {
    return 'Ha usado más del 80% de un núcleo de procesador durante $elapsed.';
  }

  @override
  String runningPhoneStuckWhy(String elapsed) {
    return 'Ha corrido cerca del 100% durante $elapsed sin ninguna conversación esperándolo.';
  }

  @override
  String get runningPhoneServerRoot =>
      'Corre un servidor. Detén el servidor desde En este teléfono.';

  @override
  String get runningPhoneConversationId =>
      'ID de la conversación de Claude Code';

  @override
  String get runningPhoneOwner => 'Pertenece a';

  @override
  String runningPhoneRowNormal(int count, String memory) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count corriendo · $memory',
      one: '1 corriendo · $memory',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowLeftBehind(int count) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count abandonados, aún usando el teléfono',
      one: '1 abandonado, aún usando el teléfono',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowHeavy(int count, String name, String percent) {
    String _temp0 = intl.Intl.pluralLogic(
      count,
      locale: localeName,
      other: '$count pesados, $name el más · $percent%',
      one: '$name está pesado · $percent%',
    );
    return '$_temp0';
  }

  @override
  String runningPhoneRowNearLimit(int count, int limit) {
    return '$count de $limit: Android puede detener a los servidores';
  }

  @override
  String get runningPhoneRowIdle => 'Ver qué corre y qué calienta al teléfono';

  @override
  String chatStuckCommand(String time) {
    return '$time · puede estar estancado';
  }

  @override
  String chatStuckMinutes(int minutes) {
    return '$minutes min';
  }

  @override
  String chatStuckHours(int hours, int minutes) {
    return '$hours h $minutes min';
  }

  @override
  String chatStuckUnnamed(String time) {
    return 'Sigue corriendo · $time · puede estar estancado';
  }

  @override
  String chatStuckWaitsNote(String command) {
    return 'Espera a que termine $command. Detenlo para avanzar ahora.';
  }

  @override
  String get chatStuckNeverEnds =>
      'Esta respuesta quizá nunca termine. Detener y enviar hace que tu mensaje llegue ahora.';

  @override
  String get chatStuckThisStep => 'este paso';

  @override
  String get kitComposerAddsToReply => 'Se agrega a esta respuesta';

  @override
  String kitComposerCantTakeMidReply(String agent) {
    return '$agent no puede tomar un mensaje a mitad de respuesta.';
  }

  @override
  String get kitTurnLiveStopShort => 'Detener';

  @override
  String get frostGlassQuestion => 'Vidrio';

  @override
  String get frostGlassOff => 'Apagado';

  @override
  String get frostGlassWeak => 'Débil';

  @override
  String get frostGlassBalanced => 'Equilibrado';

  @override
  String get frostGlassStrong => 'Fuerte';

  @override
  String get frostGlassHint =>
      'Cuando la conversación se desliza bajo la caja de mensajes o el encabezado, la desenfocan suavemente. Apagado los mantiene sólidos.';

  @override
  String get frostGlassHeldSaver =>
      'Ahorro está encendido, así que la caja de mensajes y el encabezado se quedan sólidos.';

  @override
  String get frostGlassHeldMotion =>
      'El movimiento reducido está encendido, así que la caja de mensajes y el encabezado se quedan sólidos.';

  @override
  String get frostGlassHeldContrast =>
      'El contraste alto está encendido, así que la caja de mensajes y el encabezado se quedan sólidos.';

  @override
  String get frostGlassHeldStyle =>
      'Este estilo mantiene su propio aspecto, así que la caja de mensajes y el encabezado se quedan sólidos.';

  @override
  String stopRowSemantics(String agent) {
    return 'Detener a $agent';
  }

  @override
  String phoneTeamProofSignal(int signal) {
    return 'Android cerró la prueba de seguridad (señal $signal) antes de que pudiera terminar.';
  }

  @override
  String phoneTeamProofCheckSignal(String check, int signal) {
    return 'Android cerró la revisión de seguridad “$check” (señal $signal).';
  }

  @override
  String phoneTeamProofNotBlocked(String check) {
    return 'La revisión de seguridad “$check” falló: este teléfono no la bloqueó como el sandbox del equipo necesita.';
  }

  @override
  String get phoneTeamProofSandbox =>
      'El sandbox del equipo no pudo arrancar en este teléfono.';

  @override
  String phoneTeamProofUbuntu(String check) {
    return 'El paso de Ubuntu “$check” falló dentro del sandbox del equipo.';
  }

  @override
  String get phoneTeamProofFixture =>
      'Los archivos de prueba privados de la revisión de seguridad cambiaron o no se pudieron preparar.';

  @override
  String get phoneTeamProofNoAnswer =>
      'La prueba de seguridad no respondió a tiempo.';

  @override
  String phoneTeamProofProot(String check) {
    return 'La revisión de seguridad de proot “$check” no pasó.';
  }

  @override
  String phoneTeamProofOther(String check) {
    return 'La revisión de seguridad se detuvo en “$check”.';
  }

  @override
  String get chatsNewAddAgent => 'Agregar un agente';

  @override
  String get agentPersonaAntigravityStrength => 'Antigravity en tu terminal';

  @override
  String get agentPersonaAntigravityAbout =>
      'La forma de Google, primero en la terminal, de trabajar con agentes de Antigravity.';

  @override
  String get agentPersonaAntigravityNeeds =>
      'Una cuenta de Google; inicia sesión en tu navegador';

  @override
  String get agentsAntigravitySignInIntro =>
      'Antigravity te da un enlace de inicio de sesión en la terminal. Ábrelo en tu navegador e inicia sesión con Google, y luego pega el código del navegador en la terminal.';

  @override
  String get agentsGeminiMigrationNote =>
      'Si Gemini CLI te pide usar Antigravity, vuelve a Agentes e instala Antigravity (agy). Tus conversaciones de Gemini se quedan aquí.';

  @override
  String get phoneSetupStageCompat => 'Reintentando en modo de compatibilidad';

  @override
  String get productErrorFileTooBig =>
      'This file is too big to show here (the limit is 2 MB). Open it on the computer where it lives, or ask the agent for a smaller piece.';

  @override
  String get productErrorFileMissing =>
      'That file isn\'t there any more. It may have been moved or deleted.';

  @override
  String get productErrorFileDenied =>
      'The computer won\'t let this app read that file.';

  @override
  String get productErrorFileUnreadable =>
      'This kind of file can\'t be shown here.';

  @override
  String phoneFileInstallTitle(String name) {
    return 'Install $name?';
  }

  @override
  String get phoneFileInstallBody =>
      'This app file is on this phone. Android asks you to confirm before anything is installed.';

  @override
  String get phoneFileInstallAction => 'Install app…';

  @override
  String phoneFileOpenTitle(String name) {
    return 'Open $name?';
  }

  @override
  String get phoneFileOpenBody =>
      'This file is on this phone and is too big or the wrong kind to show here. Choose which app opens it.';

  @override
  String get phoneFileOpenAction => 'Open with…';

  @override
  String get phoneFileFindTitle => 'Find the file';

  @override
  String phoneFileFindBody(String path) {
    return 'This version can\'t hand the file to another app yet. Find it in your Files app: $path';
  }

  @override
  String phoneFileWhereInstall(String name, String folder) {
    return '$name is in $folder. Open it from the Files app to install it.';
  }

  @override
  String phoneFileWhereOpen(String name, String folder) {
    return '$name is in $folder. Open it from the Files app.';
  }

  @override
  String get integrationsProvidersLoadFailed =>
      'No se pudieron cargar los proveedores';

  @override
  String get integrationsMcpServersLoadFailed =>
      'No se pudieron cargar los servidores MCP';

  @override
  String get integrationsResourcesLoadFailed =>
      'No se pudieron cargar los recursos MCP';

  @override
  String get integrationsListFailedBody =>
      'El servidor no envió esta lista. Inténtalo de nuevo.';

  @override
  String get integrationsFolderMissing =>
      'La carpeta de este proyecto ya no está en el servidor. Abre otro proyecto.';

  @override
  String get integrationsProvidersUnsupported =>
      'Este servidor no muestra proveedores';

  @override
  String get integrationsMcpServersUnsupported =>
      'Este servidor no muestra servidores MCP';

  @override
  String get integrationsResourcesUnsupported =>
      'Este servidor no muestra recursos MCP';

  @override
  String get integrationsListUnsupportedBody =>
      'Su versión no ofrece esta lista.';

  @override
  String get integrationsWaitingTitle => 'Esperando al servidor';

  @override
  String get integrationsWaitingBody =>
      'Esta página se carga sola cuando el servidor esté conectado.';

  @override
  String get crashOfferMessage =>
      'OpenCode Mobile se cerró inesperadamente la última vez. ¿Enviar un informe de fallo?';

  @override
  String get crashOfferReview => 'Revisar y enviar';

  @override
  String get crashOfferNotNow => 'Ahora no';

  @override
  String get crashOfferReportTitle => 'La aplicación se cerró inesperadamente';

  @override
  String get crashOfferSection => 'Informes de fallos';

  @override
  String get crashOfferSettingTitle => 'Ofrecer informes de fallos';

  @override
  String get crashOfferSettingDetail =>
      'Cuando la aplicación se cierre inesperadamente, ofrece un informe que puedes revisar antes. Nunca se envía nada por sí solo.';

  @override
  String get voiceAutoSetupUnknownNetwork =>
      'Este teléfono no puede saber si esta conexión gasta datos.';

  @override
  String get termuxRetiredNotice =>
      'Termux ya no es compatible. Configura Linux integrado en este teléfono o conéctate a tu ordenador. Tus proyectos en Termux siguen ahí; cópialos al almacenamiento compartido del teléfono o súbelos con Git para traerlos.';

  @override
  String get termuxRetiredSetUpLinux => 'Configurar Linux integrado';

  @override
  String get phoneSetupErrorNodeUnsupported =>
      'El sistema de este teléfono no puede ejecutar Node.js dentro de Linux integrado. Aún puedes usar OpenCode Mobile conectándote a tu ordenador.';

  @override
  String get phoneSetupConnectComputer => 'Conectar un ordenador';

  @override
  String get telemetryConsentTitle => '¿Ayudas a mejorar OpenCode Mobile?';

  @override
  String get telemetryConsentBody =>
      'Si aceptas, la aplicación envía recuentos de uso anónimos e informes de fallos al desarrollador a través de Google Firebase. Puedes desactivarlo cuando quieras en Ajustes › Privacidad y datos.';

  @override
  String get telemetryConsentSends =>
      'Se envía: qué páginas y funciones usas, hasta dónde llega la configuración e informes de fallos, con un ID de instalación aleatorio.';

  @override
  String get telemetryConsentNever =>
      'Nunca se envía: tu código, conversaciones, archivos, direcciones de servidores ni claves. Ningún ID de publicidad.';

  @override
  String get telemetryConsentShare => 'Compartir uso anónimo';

  @override
  String get telemetryConsentNotNow => 'Ahora no';

  @override
  String get telemetryUsageTitle => 'Usage and app health';

  @override
  String get telemetryUsageDetail =>
      'Sends anonymous counts to Google Analytics for Firebase: which kinds of pages and features you use, whether connecting, sending, setup, agents, the background service and updates work, how fast the app starts and how smooth pages are, memory warnings, and ranges for your phone\'s memory, free storage, CPU and WebView version. Also sends timings to Firebase Performance Monitoring: how long app start, connecting, the first reply, setup steps and starting an agent take. Only fixed words, ranges and numbers, with a random install ID, your phone model and Android version. No code, conversations, files, addresses, keys or advertising ID.';

  @override
  String get telemetryCrashesTitle => 'Crash reports';

  @override
  String get telemetryCrashesDetail =>
      'Sends crash reports to Google Firebase Crashlytics: the kind of error and where in the app\'s code it happened, never its message, the last steps you took as short words, and why Android ended the app last time. No code, conversations, files, addresses, keys or advertising ID.';

  @override
  String get telemetryConsentChoose => 'Choose';

  @override
  String get telemetryConsentSave => 'Save my choices';

  @override
  String get sendReportTitle => 'Send a problem report';

  @override
  String get sendReportRowDetail => 'Review a redacted log, then send it';

  @override
  String get sendReportSubtitle =>
      'This is everything the report holds. Nothing is sent until you tap Send.';

  @override
  String get sendReportSend => 'Send';

  @override
  String get sendReportCollecting => 'Collecting the report…';

  @override
  String get sendReportSentTitle => 'Report sent';

  @override
  String get sendReportSentBody =>
      'Quote this report code if you open an issue on GitHub, so the developer can find your report.';

  @override
  String get sendReportFailed =>
      'The report could not be sent. Check your connection and try again.';

  @override
  String get sendReportDone => 'Done';

  @override
  String get reportCodeLabel => 'Report code';

  @override
  String get reportCodeCopy => 'Copy report code';

  @override
  String chatSendFailureSignInAgain(String agent) {
    return '$agent needs you to sign in again';
  }

  @override
  String get teamRefusalServerRestarted =>
      'OpenCode restarted while this step was running, so it was cut off.';

  @override
  String get teamRefusalServerRestartedNext =>
      'Resume to send that step again. It picks up from the work already done.';

  @override
  String get teamRefusalRestartResend =>
      'OpenCode restarted while this step was running. The team is sending it again.';

  @override
  String get teamRefusalRestartResendNext =>
      'Nothing to do. Work already done is kept.';
}
