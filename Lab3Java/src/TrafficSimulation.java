import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TrafficSimulation {

    // Перечисление направлений
    public enum Direction { N, S, W, E }

    // Класс автомобиля
    public static class Car implements Runnable {
        private final Direction from;
        private final Direction to;
        private final Intersection intersection;
        private final long baseDelay;

        public Car(Direction from, Direction to, Intersection intersection, long baseDelay) {
            this.from = from;
            this.to = to;
            this.intersection = intersection;
            this.baseDelay = baseDelay;
        }

        @Override
        public void run() {
            try {
                intersection.arrive(from, to);
                System.out.println("Автомобиль едет с " + from + " на " + to);
                // Имитируем проезд через перекресток
                Thread.sleep((long) (Math.random() * baseDelay) + 200);
                intersection.leave(from, to);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // Класс перекрестка
    public static class Intersection {
        private final Lock lock = new ReentrantLock();
        private final Map<Direction, Queue<Direction>> queues = new HashMap<>();
        private final Map<Direction, Condition> conditions = new HashMap<>();

        public Intersection() {
            for (Direction d : Direction.values()) {
                queues.put(d, new LinkedList<>());
                conditions.put(d, lock.newCondition());
            }
        }

        // Метод прибытия автомобиля на перекресток
        public void arrive(Direction from, Direction to) {
            lock.lock();
            try {
                queues.get(from).add(to);
                System.out.println("Автомобиль приехал с " + from + " и хочет поехать на " + to);
                while (!canGo(from, to)) {
                    conditions.get(from).await();
                }
                queues.get(from).poll(); // Машина сейчас поедет
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }

        // Метод ухода автомобиля с перекрестка
        public void leave(Direction from, Direction to) {
            lock.lock();
            try {
                System.out.println("Автомобиль проехал перекрёсток с " + from + " на " + to);
            } finally {
                lock.unlock();
            }
        }

        // Проверка, может ли автомобиль ехать
        private boolean canGo(Direction from, Direction to) {
            // В упрощённой логике, если светофор дал сигнал, то можно ехать
            return true;
        }

        // Получение очередей
        public Map<Direction, Queue<Direction>> getQueues() {
            return queues;
        }

        // Получение условия для направления
        public Condition getCondition(Direction d) {
            return conditions.get(d);
        }

        // Получение локa
        public Lock getLock() {
            return lock;
        }
    }

    // Класс контроллера светофора
    public static class TrafficLightController implements Runnable {
        private final Intersection intersection;
        private final long checkInterval;

        public TrafficLightController(Intersection intersection, long checkInterval) {
            this.intersection = intersection;
            this.checkInterval = checkInterval;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                boolean acted = false;
                intersection.getLock().lock();
                try {
                    // Определяем, какие направления могут ехать
                    // В данной простой реализации пропускаем по одному направлению
                    if (!intersection.getQueues().get(Direction.N).isEmpty()) {
                        intersection.getCondition(Direction.N).signal();
                        acted = true;
                    }
                    if (!intersection.getQueues().get(Direction.S).isEmpty()) {
                        intersection.getCondition(Direction.S).signal();
                        acted = true;
                    }
                    if (!intersection.getQueues().get(Direction.W).isEmpty()) {
                        intersection.getCondition(Direction.W).signal();
                        acted = true;
                    }
                    if (!intersection.getQueues().get(Direction.E).isEmpty()) {
                        intersection.getCondition(Direction.E).signal();
                        acted = true;
                    }
                } finally {
                    intersection.getLock().unlock();
                }

                if (!acted) {
                    // Если нет кого пропускать, ждем некоторое время
                    try {
                        Thread.sleep(checkInterval);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    // Метод для запуска одной симуляции
    private static boolean runSimulation(String testName, List<Car> cars, long lightCheckInterval, long timeout) {
        System.out.println("==== " + testName + " ====");

        Intersection intersection = new Intersection();
        TrafficLightController controller = new TrafficLightController(intersection, lightCheckInterval);
        Thread lightThread = new Thread(controller, "Светофор");
        lightThread.start();

        List<Thread> carThreads = new ArrayList<>();
        for (Car car : cars) {
            Thread carThread = new Thread(car, "Автомобиль-" + car.from + "->" + car.to);
            carThreads.add(carThread);
            carThread.start();
        }

        // Ждем завершения автомобилей или таймаут
        long startTime = System.currentTimeMillis();
        boolean allFinished = false;

        while (System.currentTimeMillis() - startTime < timeout) {
            boolean finished = true;
            for (Thread t : carThreads) {
                if (t.isAlive()) {
                    finished = false;
                    break;
                }
            }
            if (finished) {
                allFinished = true;
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // Игнорируем
            }
        }

        // Завершаем светофор
        lightThread.interrupt();
        try {
            lightThread.join(2000);
        } catch (InterruptedException e) {
            // Игнорируем
        }

        if (!allFinished) {
            System.err.println("Не все автомобили завершили движение за " + (timeout / 1000) + " секунд. Возможен deadlock.");
            return false;
        } else {
            System.out.println("Все автомобили успешно проехали. Симуляция завершена.\n");
            return true;
        }
    }

    // Метод для запуска тестов
    private static void runTests() {
        int iterations = 5;
        boolean deadlockDetected = false;

        for (int i = 1; i <= iterations; i++) {
            System.out.println("---- Тестовая итерация " + i + " ----");
            // Генерируем случайное количество автомобилей (3-7)
            int numberOfCars = 3 + new Random().nextInt(5);
            List<Car> cars = new ArrayList<>();
            TrafficSimulation.Direction[] dirs = TrafficSimulation.Direction.values();
            long carDelay = 500; // Базовая задержка для автомобилей
            long lightCheckInterval = 500; // Интервал проверки светофора

            for (int j = 0; j < numberOfCars; j++) {
                Direction from = dirs[new Random().nextInt(dirs.length)];
                Direction to;
                do {
                    to = dirs[new Random().nextInt(dirs.length)];
                } while (to == from);
                cars.add(new Car(from, to, new Intersection(), carDelay));
            }

            boolean success = runSimulation("Тест " + i, cars, lightCheckInterval, 10_000);

            if (!success) {
                deadlockDetected = true;
                break;
            }
        }

        if (!deadlockDetected) {
            System.out.println("Deadlock не обнаружен в данных тестах.");
        } else {
            System.err.println("Обнаружен deadlock во время тестирования!");
        }

        System.out.println("Тестирование завершено.\n");
    }

    // Главный метод
    public static void main(String[] args) {
        System.out.println("=== Запуск симуляции перекрестка ===\n");

        // Изначальная ситуация: 3 автомобиля
        Intersection initialIntersection = new Intersection();
        List<Car> initialCars = new ArrayList<>();
        initialCars.add(new Car(Direction.S, Direction.W, initialIntersection, 500)); // S->W
        initialCars.add(new Car(Direction.W, Direction.E, initialIntersection, 500)); // W->E
        initialCars.add(new Car(Direction.N, Direction.S, initialIntersection, 500)); // N->S

        boolean initialSuccess = runSimulation("Изначальная симуляция", initialCars, 500, 10_000);

        if (initialSuccess) {
            System.out.println("Изначальная симуляция прошла успешно.\n");
        } else {
            System.err.println("В иначальной симуляции возник deadlock!\n");
        }

        // Запуск тестов
        runTests();

        System.out.println("=== Все симуляции завершены ===");
    }
}